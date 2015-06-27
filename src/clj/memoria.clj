 (ns memoria)
;desestructuració
(let [normal [1 2 3]                       ;normal = [1 2 3]
      [n11 n12 n13 n14] [11 12 13]             ;n11 = 11, n12 = 12, n13 = 13, n14 = nil
      [n21 & other] [21 22 23]             ;n21 = 21, other = (22, 23)
      [n31 n32 [n33 n34]] [31 32 [33 34]]  ;n31 = 31, n32 = 32, n33 = 33, n34 = 34
      {:keys [a b c]} {:a 41 :b 42 :c 43}] ;a = 41, b = 42, c = 43
  (println normal)
  (println n11 n12 n13 n14)
  (println n21 other)
  (println n31 n32 n33 n34)
  (println a b c))

;binary search tree (persistent dada structures)
(defn xconj [t v]
  (cond
    (nil? t)
      {:L nil    :val v    :R nil}
    (< v (:val v))
      {:L (xconj (:L t) v)    :val (:val t)    :R (:R t)}
    :else
      {:L (:L t)    :val (:val t)    :R (xconj (:R t) v)}))

(def tree1
  (-> nil
      (xconj 5)
      (xconj 3)
      (xconj 2)))

(def tree2 (xconj tree1 7))

(identical? (:L tree1) (:L tree2))
;=> true

;Iteracio 1
#_(defn refresh-all-clients [tweet]
  (doseq [uid (:any @connected-uids)]
    (chsk-send! uid
                [:tweets/text (:text tweet)])))

#_(defn tweets-loop []
  (go-loop []
           (let [tweet (<! tweets-chan)]
             (refresh-all-clients tweet)
             (recur))))
#_(tweets-loop)

;Iteració 3

;Servidor

#_(defn update-language-statistics [lang lang-statistics]
  (update-in lang-statistics [lang] (fnil inc 0)))

(defn update-language-statistics [lang lang-statistics]
  (if (contains? lang-statistics lang)
    (update-in lang-statistics [lang] inc)
    (assoc lang-statistics lang 1)))

(defn get-lang-statistics [lang-statistics]
  (let [[lang-statistics other] (split-at (:num-lang-statistics params) lang-statistics)]
    (conj lang-statistics (apply + (map second other)))))

(defn refresh-all-clients [tweet tick lang-statistics]
  (doseq [uid (:any @connected-uids)]
    (chsk-send! uid
                [:tweets/text (:text tweet)])
    (if (zero? tick)
      (chsk-send! uid
                  [:tweets/lang (get-lang-statistics lang-statistics)]))))

 (0 1 2 0 1 2 0 1 2 0 1 2 .....)

#_(defn next-tick [clock]
  (-> clock inc (mod (:freq-lang-statistics params))))

(defn tweets-loop []
  (go-loop [[tick & ticks] (cycle (range (:freq-lang-statistics params)))
            lang-statistics (priority-map-by >)]
           (let [tweet (<! tweets-chan)
                 updated-lang-statistics
                 (update-language-statistics (:lang tweet) lang-statistics)]
             (refresh-all-clients tweet tick updated-lang-statistics)
             (recur ticks updated-lang-statistics))))
(tweets-loop)

;Iteració 4
#_(defn- draw-chart [data div {:keys [id bounds x-axis y-axis plot series color]}]
  (let [width        (or (:width div) (:width (default-size id)))
        height       (or (:height div) (:height (default-size id)))
        data         data
        Chart        (.-chart js/dimple)
        svg          (.newSvg js/dimple (str "#" id) width height)
        dimple-chart (.setBounds (Chart. svg) (:x bounds) (:y bounds) (:width bounds) (:height bounds))
        x            (doto (.addCategoryAxis dimple-chart "x" x-axis)
                       (aset "title" nil))
        y            (doto (.addMeasureAxis dimple-chart "y" y-axis)
                       (aset "title" nil))
        s            (.addSeries dimple-chart series plot (clj->js [x y]))
        color-fn     (-> js/dimple .-color)]
    (aset s "data" (clj->js data))
    (aset dimple-chart "defaultColors" (to-array [(new color-fn color)]))
    (.addOrderRule x (sort-by-field y-axis))
    (.draw dimple-chart)))

var y = dimple-chart.addMeasureAxis("y", y-axis);
y.title = "";

;Iteració 5
;Servidor
#_(defn get-num-langs-uid [langs-clients uid]
  (get langs-clients uid (:num-lang-statistics params)))

#_(defn refresh-all-clients [tweet clock]
  (doseq [uid (:any @connected-uids)]
    #_(println "Sending to uid " uid)
    (chsk-send! uid
                [:tweets/text (:text tweet)])
    (if (zero? clock)
      (chsk-send! uid
                  [:tweets/lang (get-lang-statistics @lang-statistics
                                                     (get-num-langs-uid @num-langs-clients uid))]))))


#_(defn update-language-statistics [lang]
  (swap! lang-statistics #(update-in % [lang] (fnil inc 0))))

(defn tweets-loop []
  (go-loop [[tick & ticks] (cycle (range (:freq-lang-statistics params)))]
           (let [tweet (<! tweets-chan)]
             (update-language-statistics (:lang tweet))
             (refresh-all-clients tweet tick)
             (recur ticks))))

(defn num-langs-form [_ owner]
  (reify
    om/IInitState
    (init-state [_]
      {:num-langs ""})
    om/IRenderState
    (render-state [_ state]
      (html
        [:div
         [:label.col-xs-6 "Number of languages to show statistics:"]
         [:div.col-xs-2
          [:input.form-control {:type "text" :ref "num-langs" :value (:num-langs state)
                                :onChange #(handle-change % owner state)}]]
         [:div.col-xs-2
          [:button.btn {:type "button" :on-click #(notify-server owner)} "Select"]]]))))

(defn handle-change [e owner {:keys [num-langs]}]
  (let [value (.. e -target -value)]
    (if-not (re-find #".*\D.*" value)
      (om/set-state! owner :num-langs value)
      (om/set-state! owner :num-langs num-langs))))

(. (. e -target) -value)