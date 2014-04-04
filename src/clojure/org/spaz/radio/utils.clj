(ns org.spaz.radio.utils
  (:require [neko.activity :as activity :refer [defactivity set-content-view!]]
            [neko.threading :as threading :refer [on-ui]]
            [neko.context :as context]
            [neko.find-view :as view]
            [neko.notify :as notify]
            [net.clandroid.service :as service]
            [neko.resource :as r]
            [neko.log :as log]
            [neko.ui :as ui :refer [make-ui]])
  (:import android.media.MediaPlayer
           android.content.ComponentName
           android.content.pm.PackageInfo
           android.content.Intent))

(defonce ^:const package-name "org.spaz.radio")
(defonce ^:const end-service-signal "END_SPAZ_PLAYER_SERVICE")
(defonce ^:const end-alarm-signal "END_SPAZ_PLAYER_ALARM")
(defonce ^:const main-activity-signal "org.spaz.radio.MAIN")
(defonce ^:const alarm-service-name "org.spaz.radio.AlarmService")
(defonce ^:const player-service-name "org.spaz.radio.PlayerService")

;; THIS CANNOT BE A RESOURCE!
(defonce ^:const playing-service-id 42) 


;; this is here only because it has to be
(defonce needs-alarm (atom #{}))

(defn log-and-toast
  [& msgs]
  (try 
    (let [msg  (->> msgs (interpose " ") (apply str))]
      (log/i msg)
      (on-ui
       (notify/toast msg)))
    (catch Exception e
      nil)))



;; TODO: all the flags!
;;  PendingIntent.FLAG_UPDATE_CURRENT);
;;    how the hell? just blow off notify/notification?
;; notification.flags |= Notification.FLAG_ONGOING_EVENT;
;;    try after the fact?

(defn notification
  [^android.content.Context context text]
  (notify/notification :icon (r/get-resource context :drawable :ic_launcher)
                       :content-title "SPAZ Radio"
                       :content-text text
                       ;; TODO: must also Intent.FLAG_ACTIVITY_NEW_TASK somehow
                       :action [:activity main-activity-signal]))


(defn renotify
  [^android.content.Context context text]
  ;; MUST use resource here since start-foreground requires an id
  ;; can't use the neko notification id atom becasue it's private :-/
  (notify/fire playing-service-id (notification context text)))


(defn start-activity
  "moved to utilza"
  [^String pkg-name activity-name]
  (.startActivity context/context
                  (doto (Intent.)
                    (.setAction Intent/ACTION_MAIN)
                    (.addFlags Intent/FLAG_ACTIVITY_NEW_TASK)
                    (.setComponent  (ComponentName. pkg-name (str pkg-name "." activity-name))))))


(defn get-version-info
  "copy/paste from utilza"
  [^String package-name]
  (let [pi (-> context/context
               .getPackageManager
               (.getPackageInfo package-name 0))]
    {:version-name (.versionName pi)
     :version-number (.versionCode pi)}))



(comment



  (get-version-info package-name)
  
  

  )