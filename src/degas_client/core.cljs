(ns degas-client.core
  (:require [reagent.core :as reagent :refer [atom]]
            [wscljs.client :as ws]
            [wscljs.format :as fmt]

            [degas.individual :as i]
            [degas.crossover :as c]
            [degas.mutation :as m]
            [degas.population :as p]
            [degas.selection :as s]
            [degas.evolution :as e]
            [degas.fitness :as f]
            [degas.helpers :as h]
            ))


;; ========

(def distances [[ 0   28  57  72  81  85  80  113 89  80  ]
                [ 28  0   28  45  54  57  63  85  63  63  ]
                [ 57  28  0   20  30  28  57  57  40  57  ]
                [ 72  45  20  0   10  20  72  45  20  45  ]
                [ 81  54  30  10  0   22  81  41  10  41  ]
                [ 85  57  28  20  22  0   63  28  28  63  ]
                [ 80  63  57  72  81  63  0   80  89  113 ]
                [ 113 85  57  45  41  28  80  0   40  80  ]
                [ 89  63  40  20  10  28  89  40  0   40  ]
                [ 80  63  57  45  41  63  113 80  40  0   ]])

(defn get-distance [[a b]]
  (get-in distances [a b]))

(defn pathify [vec]
  "Turns a vector into a vector of transitions,
   including last element back to the first
   ex: [0 1 2] -> [[0 1] [1 2] [2 0]]"
  (map (fn [i]
           (subvec (into vec vec) i (+ 2 i)))
       (range (count vec))))

(defn fitness-tsm [ind]
  "Returns the total distance travelled for a given solution"
  (let [path (pathify ind)
        distances (map get-distance path)]
    (reduce + distances)))

(def population (atom []))
(reset! population (p/rand-pop 10 4 i/rand-unique-ind))
(def tempopulation (atom []))
;; (reset! tempopulation [])
(def best (atom (first @population)))

(defn evolve []
  (dotimes [i 5]
    (reset! population
            (e/next-generation @population
                               fitness-tsm
                               s/tournament-selection
                               { c/crossover-ordered 1 }
                               { m/mut-swap 1}
                               0.2
                               ))
    (reset! best (first @population))
    )
  )

(evolve)

;; ========














(enable-console-print!)
(defonce message (atom "Nothing yet..."))

(defn messages []
  [:div [:h2 "Websocketsp!"]
   [:p (prn-str @best)]
   [:p (prn-str (fitness-tsm @best))]
   [:div (prn-str @message)]])

(reagent/render-component [messages]
                          (. js/document (getElementById "app")))

(def handlers {:on-message (fn [e] (reset! message (cljs.reader/read-string (.-data e))))
               :on-open    #(prn "Opening a new connection")
               :on-close   #(prn "Closing a connection")})

(def socket (ws/create "ws://localhost:3450" handlers))

(ws/send socket [[1] [2] [3]] fmt/edn)
(ws/send socket {:name "Jasds"} fmt/edn)

(ws/close socket)


(defn init! []
  (let [ws (js/WebSocket. "ws://localhost:3450")]
    (aset ws "onmessage" (fn [m] (swap! message (fn [] (aget m "data")))))))

(defn on-js-reload [])
