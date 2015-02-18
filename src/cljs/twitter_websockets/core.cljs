(ns twitter-websockets.core
  (:require-macros [cljs.core.async.macros :as asyncm :refer (go go-loop)])
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [taoensso.sente  :as sente :refer (cb-success?)]
            [cljs.core.async :as async :refer (<! >! put! chan)]))

(enable-console-print!)

(defonce app-state (atom {:text "Hello Chestnut!"}))

; WebSockets

(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/chsk" ; Note the same path as before
                                  {:type :auto ; e/o #{:auto :ajax :ws}
                                   })]
  (def chsk       chsk)
  (def ch-chsk    ch-recv) ; ChannelSocket's receive channel
  (def chsk-send! send-fn) ; ChannelSocket's send API fn
  (def chsk-state state)   ; Watchable, read-only atom
  )

(defn main []
  (go-loop []
             (let [{:keys [event]} (<! ch-chsk)
                   [ev-id ev-data] event]
               (println "He rebut això: " ev-data "PROU!!!!"))
             (recur)))
