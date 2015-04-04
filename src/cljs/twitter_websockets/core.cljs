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
                 [ev-id ev-value] event]
             (if (= :chsk/recv ev-id)
               (let [[_ val] ev-value]
                 #_(println "::::::" val)
                 (if (or (not (string? val)) (not (clojure.string/blank? val)))
                   (om/transact! cursor [:tweets] #(take 10 (into [val] %)))))))
           (recur)))

(defn tweets-view [{:keys [tweets]} owner]
  (reify
    om/IRender
    (render [_]
      (html
        [:div [:h3 "Recived Tweets:"]
         [:ul (map #(vector :li %) tweets)]]
        ))))

(defn application [cursor owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (event-loop cursor owner))
    om/IRender
    (render [_]
      (html
        [:div
         [:h1 "Twitter Streaming"]
         (om/build tweets-view cursor)]))))


(defn main []
  (om/root application app-state
           {:target (. js/document (getElementById "tweets"))}))