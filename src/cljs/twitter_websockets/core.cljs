(ns twitter-websockets.core
  (:require-macros [cljs.core.async.macros :as asyncm :refer (go go-loop)])
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [taoensso.sente :as sente :refer (cb-success?)]
            [sablono.core :as html :refer-macros [html]]
            [cljs.core.async :as async :refer (<! >! put! chan)]))

(enable-console-print!)

(defonce app-state (atom {:tweets ["Hello Chestnut!" "Good bye!!"]}))

; WebSockets

(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/chsk"                   ; Note the same path as before
                                  {:type :auto              ; e/o #{:auto :ajax :ws}
                                   })]
  (def chsk chsk)
  (def ch-chsk ch-recv)                                     ; ChannelSocket's receive channel
  (def chsk-send! send-fn)                                  ; ChannelSocket's send API fn
  (def chsk-state state)                                    ; Watchable, read-only atom
  )

(defn event-loop [cursor owner]
  (go-loop []
           (let [{:keys [event]} (<! ch-chsk)
                 [ev-id ev-data] event]
             (println (last ev-data))
             (om/transact! cursor [:tweets] #(conj % (last ev-data))))
           (recur)))

(defn tweets-view [{:keys [tweets]}]
  (reify
    om/IRender
    (render [_]
      (html
        [:h3 "Recived Tweets:"]
        (map (dom/p nil %) tweets)
        ))))

(defn application [cursor owner]
  (reify
    ;om/IWillMount
    ;(will-mount [_]
    ;  (event-loop cursor owner))
    om/IRender
    (render [_]
      (html
        [:h1 "Twitter Streaming"]
        (om/build tweets-view cursor)))))


(defn main []
  (println "Hello!")
  (om/root application app-state
           {:target (. js/document (getElementById "tweets"))}))