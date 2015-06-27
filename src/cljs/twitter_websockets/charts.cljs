(ns twitter-websockets.charts
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [om.core :as om :include-macros true]
            [cljs.core.async :refer [<! >! chan put!]]
            [sablono.core :as html :refer-macros [html]]))

(enable-console-print!)

(defn default-size [id]
  (let [e (.getElementById js/document id)
        x (.-clientWidth e)
        y (.-clientHeight e)]
    {:width x :height y}))

(defn sort-by-field [field]
  (fn [x y]
    (let [v1 (if (= (str (aget x "language")) "Other") 0 (first (aget x field)))
          v2 (if (= (str (aget y "language")) "Other") 0 (first (aget y field)))]
      (- v2 v1))))

(defn- draw-chart [data div {:keys [id bounds x-axis y-axis plot series color]}]
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

(defn bar-chart
  "Simple bar chart done using dimple.js"
  [{:keys [data div]} owner {:keys [id] :as opts}]
  (reify
    om/IWillMount
    (will-mount [_]
      (.addEventListener js/window
                         "resize" (fn []
                                    (let [e (.getElementById js/document id)
                                          x (.-clientWidth e)
                                          y (.-clientHeight e)]
                                      (om/update! div :size {:width x :height y})))))
    om/IRender
    (render [_]
      (html
       [:div {:id id :style {:height "90%"}}]))
    om/IDidMount
    (did-mount [_]
      #_(let [n (.getElementById js/document id)]
        (while (.hasChildNodes n)
          (.removeChild n (.-lastChild n))))
      (draw-chart data div opts))
    om/IDidUpdate
    (did-update [_ _ _]
      (let [n (.getElementById js/document id)]
        (while (.hasChildNodes n)
          (.removeChild n (.-lastChild n))))
      (draw-chart data div opts))))

(defn- draw-horizontal-chart [data div {:keys [id bounds x-axis y-axis plot series color]}]
  (let [width        (or (:width div) (:width (default-size id)))
        height       (or (:height div) (:height (default-size id)))
        data         data
        Chart        (.-chart js/dimple)
        svg          (.newSvg js/dimple (str "#" id) width height)
        dimple-chart (.setBounds (Chart. svg) (:x bounds) (:y bounds) (:width bounds) (:height bounds))
        x            (doto (.addMeasureAxis dimple-chart "x" x-axis)
                           (aset "title" nil))
        y            (doto (.addCategoryAxis dimple-chart "y" y-axis)
                           (aset "title" nil))
        s            (.addSeries dimple-chart series plot (clj->js [x y]))
        color-fn     (-> js/dimple .-color)]
    (aset s "data" (clj->js data))
    (aset dimple-chart "defaultColors" (to-array [(new color-fn color)]))
    (.draw dimple-chart)))

(defn horizontal-bar-chart
  "Simple bar chart done using dimple.js"
  [{:keys [data div]} owner {:keys [id] :as opts}]
  (reify
    om/IWillMount
    (will-mount [_]
      (.addEventListener js/window
                         "resize" (fn []
                                    (let [e (.getElementById js/document id)
                                          x (.-clientWidth e)
                                          y (.-clientHeight e)]
                                      (om/update! div :size {:width x :height y})))))
    om/IRender
    (render [_]
      (html
        [:div {:id id :style {:height "90%"}}]))
    om/IDidMount
    (did-mount [_]
      (draw-horizontal-chart data div opts))
    om/IDidUpdate
    (did-update [_ _ _]
      (let [n (.getElementById js/document id)]
        (while (.hasChildNodes n)
          (.removeChild n (.-lastChild n))))
      (draw-horizontal-chart data div opts))))

