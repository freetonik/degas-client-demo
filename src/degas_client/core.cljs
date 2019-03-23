(ns degas-client.core
    (:require [reagent.core :as reagent :refer [atom]]))

(enable-console-print!)
(defonce message (atom nil))

(defn messages []
  [:div [:h2 "Websockets!"]
   (if (not (nil? @message)) [:div @message])])

(reagent/render-component [messages]
                          (. js/document (getElementById "app")))

(defn init! []
  (let [ws (js/WebSocket. "ws://localhost:3450")]
    (aset ws "onmessage" (fn [m] (swap! message (fn [] (aget m "data")))))))

(defn on-js-reload [])
