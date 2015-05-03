(ns twitter-websockets.core
  (:require-macros [cljs.core.async.macros :as asyncm :refer (go go-loop)])
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [taoensso.sente :as sente :refer (cb-success?)]
            [sablono.core :as html :refer-macros [html]]
            [cljs.core.async :as async :refer (<! >! put! chan)]))

(enable-console-print!)

(defonce app-state (atom {:tweets []
                          :statics {:length {:-40 0 :40-80 0 :80-120 0 :120+ 0}}}))

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

(defn get-bucket [length]
  (condp > length
    40 :-40
    80 :40-80
    120 :80-120
    :120+))

(defmulti handle-event (fn [[type _] _] type))

(defmethod handle-event :tweets/text [[_ tweet] cursor]
  (if (or (not (string? tweet)) (not (clojure.string/blank? tweet)))
    (let [length (count tweet)]
      (om/transact! cursor [:tweets] #(take 10 (into [tweet] %)))
      (om/transact! cursor [:statics :length] #(update-in % [(get-bucket length)] inc)))))

(defmethod handle-event :tweets/lang [[_ langs] cursor]
  (om/transact! cursor [:statics] #(assoc-in % [:langs] langs)))

(defn event-loop [cursor owner]
  (go-loop []
           (let [{:keys [event]} (<! ch-chsk)
                 [ev-id ev-value] event]
             (if (= :chsk/recv ev-id)
               (handle-event ev-value cursor))
             #_(println (get-in @app-state [:statics])))
           (recur)))

(defn tweets-view [{:keys [tweets]} owner]
  (reify
    om/IRender
    (render [_]
      (html
        [:div [:h3 "Recived Tweets:"]
         (map #(vector :p %) tweets)
        ]))))

(defn length-view [cursor owner]
  (reify
    om/IRender
    (render [_]
      (html
        [:div [:h4 "Number of letters of the tweets:"]
         [:div (str "Less than 40: " (:-40 cursor))]
         [:div (str "From 40 to 80: " (:40-80 cursor))]
         [:div (str "From 80 to 120: " (:80-120 cursor))]
         [:div (str "More than 120: " (:120+ cursor))]
         ]))))

(defn langs-view [cursor owner]
  (reify
    om/IRender
    (render [_]
      (html
        [:div [:h4 "Most used languages of the tweets:"]
         (map (fn [lang-statistic]
                (if (vector? lang-statistic)
                   [:div (str "Language " (first lang-statistic) ": " (second lang-statistic))]
                   [:div (str "Other languages: " lang-statistic)])) cursor)]))))

(defn statics-view [cursor owner]
  (reify
    om/IRender
    (render [_]
      (html
        [:div [:h3 "Tweets Statics:"]
         (om/build length-view (:length cursor))
         (om/build langs-view (:langs cursor))]))))

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
         (om/build tweets-view cursor)
         (om/build statics-view (:statics cursor))]))))


(defn main []
  (om/root application app-state
           {:target (. js/document (getElementById "tweets"))}))