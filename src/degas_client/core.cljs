(ns degas-client.core
  (:require [reagent.core :as reagent :refer [atom]]
            [wscljs.client :as ws]
            [wscljs.format :as fmt]))

(enable-console-print!)
(defonce message (atom "Nothing yet..."))

(defn messages []
  [:div [:h2 "Websocketsp!"]
   [:div (prn-str @message)]])

(reagent/render-component [messages]
                          (. js/document (getElementById "app")))

(def handlers {:on-message (fn [e] (reset! message (cljs.reader/read-string (.-data e))))
               :on-open    #(prn "Opening a new connection")
               :on-close   #(prn "Closing a connection")})

(def socket (ws/create "ws://localhost:3450" handlers))

(ws/send socket [[1] [2] [3]] fmt/edn)

(ws/close socket)


(defn init! []
  (let [ws (js/WebSocket. "ws://localhost:3450")]
    (aset ws "onmessage" (fn [m] (swap! message (fn [] (aget m "data")))))))

(defn on-js-reload [])
