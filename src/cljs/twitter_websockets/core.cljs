(ns twitter-websockets.core
  (:require-macros [cljs.core.async.macros :as asyncm :refer (go go-loop)])
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [taoensso.sente :as sente :refer (cb-success?)]
            [sablono.core :as html :refer-macros [html]]
            [cljs.core.async :as async :refer (<! >! put! chan)]
            [twitter-websockets.charts :as charts]
            [twitter-websockets.languages :refer (langs-traduction)]))

(enable-console-print!)

(defonce app-state (atom {:tweets     []
                          :statistics {:length {:title "Tweet Length"
                                                :data  {:-40 0 :40-80 0 :80-120 0 :120+ 0}
                                                :div   {:width "100%" :height "100%"}}
                                       :langs {:title "Tweet Language"
                                                :data  []
                                                :div   {:width "100%" :height "100%"}}}}))

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

(defn- get-bucket [length]
  (condp > length
    40 :-40
    80 :40-80
    120 :80-120
    :120+))

(defn- reformat-lang [lang]
  (if (vector? lang)
    (let [[lang-key count] lang]
      {:language (or ((keyword lang-key) langs-traduction) lang-key) :count count})
    {:language "Other" :count lang}))

(defn- reformat-length [[length count]]
  {:length length :count count})

(defmulti handle-event (fn [[type _] _] type))

(defmethod handle-event :tweets/text [[_ tweet] cursor]
  (if (or (not (string? tweet)) (not (clojure.string/blank? tweet)))
    (let [length (count tweet)]
      (om/transact! cursor [:tweets] #(take 10 (into [tweet] %)))
      (om/transact! cursor [:statistics :length :data] #(update-in % [(get-bucket length)] inc)))))

(defmethod handle-event :tweets/lang [[_ langs] cursor]
  (om/transact! cursor [:statistics :langs] #(assoc-in % [:data] (map reformat-lang langs))))

(defmethod handle-event :default [event]
  #_(println "Received:" event))

(defn event-loop [cursor owner]
  (go-loop []
           (let [{:keys [event]} (<! ch-chsk)
                 [ev-id ev-value] event]
             (when (= :chsk/recv ev-id)
               (handle-event ev-value cursor)))
           (recur)))

(defn tweets-view [{:keys [tweets]} owner]
  (reify
    om/IRender
    (render [_]
      (html
        [:div [:h3 "Recived Tweets:"]
         (map #(vector :p %) tweets)
        ]))))

(defn length-view [{:keys [title data div] :as cursor} owner]
  (reify
    om/IRender
    (render [_]
      (html
        [:div [:h4 title]
         (om/build charts/horizontal-bar-chart {:div div :data (map reformat-length data)}
                   {:opts {:id "lenth-chart"
                           :bounds {:x "5%" :y "5%" :width "90%" :height "80%"}
                           :x-axis "count"
                           :y-axis "length"
                           :plot js/dimple.plot.bar
                           :color "#2ECCFA"}})]))))

(defn submit-code [_]
  (let [nlangs (-> js/document
                   (.getElementById "nlangs"))]
    #_(print "Sent: " [:post-to-screen/code code])
    (chsk-send! [:twitter_websockets/langs-count {:nlangs (.-value nlangs) :uid (:uid @chsk-state)}])))

(defn post-form [_ _]
  (reify
    om/IRender
    (render [_]
      (html
        [:div
         [:label.col-xs-6 "Number of languages to show statistics:"]
         [:div.col-xs-2
          [:input.form-control {:id "nlangs"}]]
         [:div.col-xs-2
          [:button.btn {:type "button" :on-click (partial submit-code)} "Select"]]]))
    om/IDidMount
    (did-mount [_]
      (-> js/document
          (.getElementById "nlang")
          .focus))))

(defn langs-view [{:keys [title data div] :as cursor} owner]
  (reify
    om/IRender
    (render [_]
      (html
        [:div [:h4 title]
         (om/build charts/bar-chart {:data data :div div}
                   {:opts {:id "langs-chart"
                           :bounds {:x "5%" :y "5%" :width "90%" :height "80%"}
                           :x-axis "language"
                           :y-axis "count"
                           :plot js/dimple.plot.bar
                           :color "#d62728"}})
         (om/build post-form cursor)]))))

(defn statistics-view [cursor owner]
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
         [:h1 {:class "text-center"} "Twitter Streaming"]
         [:div {:class "col-md-6"} (om/build tweets-view cursor)]
         [:div {:class "col-md-6"} (om/build statistics-view (:statistics cursor))]]))))


(defn main []
  (om/root application app-state
           {:target (. js/document (getElementById "tweets"))}))