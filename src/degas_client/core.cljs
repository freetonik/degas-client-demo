(ns degas-client.core
  (:require [reagent.core :as reagent :refer [atom]]
            [wscljs.client :as ws]
            [wscljs.format :as fmt]
            [clojure.core.async :refer [>! <! go chan timeout alts!]]

            [degas.individual :as i]
            [degas.crossover :as c]
            [degas.mutation :as m]
            [degas.population :as p]
            [degas.selection :as s]
            [degas.evolution :as e]
            [degas.fitness :as f]
            [degas.helpers :as h]
 ))

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

(defn get-distance [[a b]] (get-in distances [a b]))

(defn fitness-tsm [ind]
  "Returns the total distance travelled for a given solution"
  (let [path (h/pathify ind)
        distances (map get-distance path)]
    (- (reduce + distances))))

;; =====
;; State
(defonce generation (atom 0))
(defonce population (atom []))
(defonce current-best (atom (first @population)))
(defonce best (atom (first @population)))

(defonce mutation-rate (atom 0.2))
(defonce elitism-rate (atom 0.01))

;; ===========
;; Async state
(defonce running? (atom false))
(def queue (chan))

(defn update-best []
  (if (> (fitness-tsm @current-best) (fitness-tsm @best))
    (reset! best @current-best)))

(defn evolve-step []
  (reset! population
          (sort-by fitness-tsm
                   (e/next-generation @population
                                      fitness-tsm
                                      { s/tournament-selection 2,
                                        s/random-selection 1}
                                      { c/crossover-ordered 1 }
                                      { m/mut-swap 1, m/mut-shift 1}
                                      @mutation-rate
                                      @elitism-rate
                                      ))))

(defn stop-async []
  (reset! running? false))

(defn reset-state []
  (stop-async)
  (reset! population (into [] (sort-by fitness-tsm (p/rand-pop 10 10 i/rand-unique-ind))))
  (reset! best (first @population))
  (reset! current-best (first @population))
  (reset! generation 0))

(defn run-async []
  (reset! running? true)

  (go
    (while @running?
      (>! queue 1)
      (<! (timeout 150))))

  ;; CONSUMER
  (go
    (while true
      (let [item (<! queue)]

        (evolve-step)
        (reset! current-best (first @population))
        (update-best)

        (swap! generation inc)))))


;; (s/tournament-selection @population fitness-tsm)

;; (map #(h/weighted-rand-choice { m/mut-swap 1, m/mut-shift 1}) (range 2))
;; (m/mutate-pop (take 2 @population) { m/mut-swap 1, m/mut-shift 1} 1)
;; (m/mutate-pop @population { m/mut-swap 1, m/mut-shift 1} 0.2)

;; (map (h/weighted-rand-choice { m/mut-swap 1, m/mut-shift 1}) (take 2 @population))

;; (defn next-generation-2
;;   []
;;   (let [
;;         popsize         (count @population)
;;         elite-count     (Math/floor (* 0 popsize))
;;         offspring-count (- popsize elite-count)

;;         crossover-fn    (h/weighted-rand-choice { c/crossover-ordered 1 })
;;         mutation-fn     (h/weighted-rand-choice { m/mut-swap 1 })

;;         sorted-pop      (sort-by fitness-tsm @population)
;;         elite-pop       (into [] (take elite-count sorted-pop))
;;         breedable-pop   (m/mutate-pop (take-last offspring-count sorted-pop)
;;                                       mutation-fn
;;                                       0.35)
;;         ]

;;     (into elite-pop (repeatedly offspring-count
;;                                            #(apply crossover-fn
;;                                                    (s/tournament-selection breedable-pop fitness-tsm)))

;;                                )))









;; ========














(enable-console-print!)
(defonce message (atom "Nothing yet..."))

(defn render-run-button []
  (if @running?
    [:button.btn.btn-success.mr-3 {:on-click stop-async} "Pause"]
    [:button.btn.btn-success.mr-3 {:on-click run-async} "Run"]))

(defn render-slider [value min max callback]
  [:input {:type "range" :value value :min min :max max
           :style {:width "100%"}
           :on-change callback}])


(defn update-mutation-rate [e]
  (reset! mutation-rate (/ (int (.. e -target -value)) 100)))

(defn render-mutation-rate-slider []
  (render-slider (* 100 @mutation-rate) 0 100 update-mutation-rate))

(defn messages []
  [:div [:h2 "Generation " @generation]
   [:p
    [:span (prn-str @mutation-rate)]
    [:br]
    [render-mutation-rate-slider]]
   [render-run-button]
   [:p
    (prn-str @current-best)
    "  --->  "
    (prn-str (fitness-tsm @current-best))]

   [:p (map #(prn-str %) (take 10 @population))]

   [:hr]
   [:h2 "Best so far:"]
   [:p
    (prn-str @best)
    "  --->  "
    [:strong (prn-str (fitness-tsm @best))]]

   [:div (prn-str @message)]])

(reagent/render-component [messages]
                          (. js/document (getElementById "app")))


(defn on-js-reload [])

;; (def handlers {:on-message (fn [e] (reset! message (cljs.reader/read-string (.-data e))))
;;                :on-open    #(prn "Opening a new connection")
;;                :on-close   #(prn "Closing a connection")})

;; (def socket (ws/create "ws://localhost:3450" handlers))

;; (ws/send socket [[1] [2] [3]] fmt/edn)
;; (ws/send socket {:name "Jasds"} fmt/edn)

;; (ws/close socket)


;; (defn init! []
;;   (let [ws (js/WebSocket. "ws://localhost:3450")]
;;     (aset ws "onmessage" (fn [m] (swap! message (fn [] (aget m "data")))))))

;; (defn evolve []
;;   (dotimes [i 500]
;;     (js/console.log "\n\n===Starting =====")
;;     (evolve-step)
;;     (reset! current-best (first @population))
;;     (update-best)
;;     (js/console.log "=================")
;;     )
;;   )

;; (evolve)
