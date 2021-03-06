(ns org.spaz.radio.media
  (:require [neko.activity :as activity :refer [defactivity set-content-view!]]
            [neko.threading :as threading :refer [on-ui]]
            [neko.notify :as notify]
            [neko.resource :as r]
            [neko.context :as context]
            [cheshire.core :as json]
            [org.spaz.radio.utils :as utils]
            [org.spaz.radio.playing :as playing]
            [neko.log :as log]
            [neko.ui :as ui :refer [make-ui]])
  (:import android.media.MediaPlayer
           android.media.AudioManager
           android.os.PowerManager
           android.net.wifi.WifiManager
           android.net.wifi.WifiInfo
           android.net.NetworkInfo
           android.media.AudioManager
           java.io.StringWriter
           java.io.PrintWriter
           android.net.wifi.SupplicantState))


(defonce mp (atom nil))

(defonce datasource (atom "http://spazradio.bamfic.com:8050/radio"))
(defonce last-pos (atom 0))

(declare assure-mp start clear release-lock)


(def wifi-lock (atom nil))

(defn set-lock
  []
  (try
    (.acquire
     (or @wifi-lock
         (swap! wifi-lock
                #(or %
                     (-> :wifi
                         context/get-service
                         (.createWifiLock WifiManager/WIFI_MODE_FULL "SpazPlayerLock"))))))
    (catch Exception e
      (log/e "wifilock bug in google")
      (release-lock))))




(defn release-lock
  []
  (and @wifi-lock
       (.isHeld @wifi-lock)
       (.release @wifi-lock)))


(defn setup-ducking
  []
  (-> :audio
      context/get-service
      (.requestAudioFocus
       (reify android.media.AudioManager$OnAudioFocusChangeListener
         (onAudioFocusChange [this i]
           ((case i
              AudioManager/AUDIOFOCUS_GAIN #(.setVolume (assure-mp) 1.0 1.0)
              AudioManager/AUDIOFOCUS_LOSS clear ;;#(log/i "audio focus loss, ignoring it")
              AudioManager/AUDIOFOCUS_LOSS_TRANSIENT #(and (.isPlaying (assure-mp))
                                                           (.pause (assure-mp)))
              AudioManager/AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK #(and (.isPlaying (assure-mp))
                                                                    (.setVolume (assure-mp) 0.1 0.1))
              #(log/e "audio focus: no matching clause for " i)))))
       AudioManager/STREAM_MUSIC AudioManager/AUDIOFOCUS_GAIN)))


(defn decode-error
  [i]
  (-> {MediaPlayer/MEDIA_INFO_BUFFERING_START   "Buffering..."
       MediaPlayer/MEDIA_INFO_BUFFERING_END  "Reconnected"}
      (get i)))



(defn start*
  [^MediaPlayer mp]
  (set-lock)
  (try
    (doto mp
      .reset ;; it doesn't hurt to be sure
      (.setOnPreparedListener
       (reify android.media.MediaPlayer$OnPreparedListener
         (onPrepared [this mp]
           (log/i "prepared" (.getCurrentPosition mp))
           (.setVolume mp 1.0 1.0)
           (setup-ducking)
           ;;(future (utils/renotify context/context @playing/last-playing)) ;; XXX unnecessary??
           (.start mp))))

      (.setOnBufferingUpdateListener 
       (reify android.media.MediaPlayer$OnBufferingUpdateListener
         (onBufferingUpdate [this mp percent]
           (reset! last-pos (.getCurrentPosition mp))
           #_(log/d "buffered" percent "% and pos" (.getCurrentPosition mp)))))
      
      (.setOnCompletionListener 
       (reify android.media.MediaPlayer$OnCompletionListener
         (onCompletion [this mp]
           (log/i "lost connection" (.getCurrentPosition mp))
           ;;(future (utils/renotify context/context "Disconnected, reconnecting..."))
           (clear)
           (start))))
      
      (.setOnSeekCompleteListener 
       (reify android.media.MediaPlayer$OnSeekCompleteListener
         (onSeekComplete [this mp]
           (log/i "seek complete" (.getCurrentPosition mp))
           )))

      (.setOnErrorListener 
       (reify android.media.MediaPlayer$OnErrorListener
         (onError [this mp what extra]
           (log/i "error" what extra (.getCurrentPosition mp))
           (.reset mp) ;; must do this, otherwise nothing else will work.
           false))) ;; grab the error and hold on to it

      (.setOnInfoListener
       (reify android.media.MediaPlayer$OnInfoListener
         (onInfo [this mp what extra]
           (let [m (decode-error what)]
             ;;(future (some->> m (utils/renotify context/context)))
             (log/i "info" m what extra (.getCurrentPosition mp)))
           false))) ;; let others handle it

      (.setAudioStreamType AudioManager/STREAM_MUSIC)
      (.setWakeMode context/context  PowerManager/PARTIAL_WAKE_LOCK)
      (.setDataSource  @datasource)
      .prepareAsync)

    
    (catch Exception e
      (log/e "error in start*")
      (.printStackTrace e)
      ;; very important! reaase the kraken!
      (clear))))


(defn assure-mp
  "Returns the mp. Will reset it if it doesn't exist"
  []
  (or @mp (swap! mp (fn [mp] (or mp
                                 (doto (MediaPlayer.)
                                   start*))))))


(defn start []
  (assure-mp))

(defn clear []
  (when @mp
    (release-lock)
    (.release @mp)
    (reset! mp nil)))


(comment
  ;; for mocking

  (def fake-server "192.168.0.46")
  (def fake-server "192.168.43.169")


  (reset! datasource (str "http://" fake-server ":8000/stream"))
  (reset! datasource (str "http://" fake-server "/test.ogg"))
  
  )