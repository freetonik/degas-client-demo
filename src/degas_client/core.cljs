(ns degas-client.core
  (:require [reagent.core :as reagent :refer [atom]]
            [wscljs.client :as ws]
            [wscljs.format :as fmt]
            [clojure.core.async :refer [>! <! go chan timeout]]
            [cljs.reader :refer [read-string]]
            [degas-client.tsm :refer [distances cities get-distance fitness-tsm]]
            [degas-client.views :as v]
            [degas-client.drawing :as dr]
            [degas.individual :as i]
            [degas.crossover :as c]
            [degas.mutation :as m]
            [degas.population :as p]
            [degas.selection :as s]
            [degas.evolution :as e]
            [degas.fitness :as f]
            [degas.helpers :as h]
            [devtools.defaults :as d]))

(enable-console-print!)

(declare comm-handlers)
(declare send-message)
(declare socket)
(declare ctx)
;; (defonce server-addr (atom "ws://localhost:5000"))
(defonce server-addr (atom "wss://aqueous-plains-54322.herokuapp.com"))

(def popsize   20)
(def indlength 50)

;; State
(defonce admin? (atom false))
(defonce online? (atom false))
(defonce authenticated? (atom false))
(defonce ratings (atom {}))
(defonce username (atom ""))
(defonce generation (atom 0))
(defonce population (atom []))
(defonce current-best (atom (first @population)))
(defonce best (atom (first @population)))
(defonce mutation-rate (atom (/ (rand-int 30) 100)))
(defonce elitism-rate (atom (/ (rand-int 8) 100)))

(defonce selections-map (atom { s/tournament-selection 20, s/random-selection 5 }))
(defonce mutations-map (atom { m/mut-swap 1, m/mut-shift 1, m/mut-reverse 1}))
(defonce crossovers-map (atom { c/crossover-ordered 1 }))

;; Async state
(defonce running? (atom false))
(def queue (chan 10))
(def upload-queue (chan 10))

;; Evolution
(defn update-best [current-best best]
  (if (> (fitness-tsm @current-best) (fitness-tsm @best))
    (reset! best @current-best)))

(defn evolve-step []
  (reset! population
          (sort-by fitness-tsm
                   (e/next-generation @population
                                      fitness-tsm
                                      @selections-map
                                      @crossovers-map
                                      @mutations-map
                                      @mutation-rate
                                      @elitism-rate))))

;; Async evolution
(defn stop-async [] (reset! running? false))

(defn run-async []
  (reset! running? true)

  (go (while @running?
        (>! queue @generation)
        (<! (timeout 500))))

  (go (while true
        (let [item (<! queue)]
          (evolve-step)
          (reset! current-best (first @population))
          (update-best current-best best)
          (dr/clear-canvas ctx 620 740)
          (doall (map #(dr/draw-city ctx (first %) (second %)) cities))
          (dr/draw-pahts ctx cities (h/pathify @current-best))
          (swap! generation inc)))))

;; Upload
(defn run-upload-async [sock]
  (go (while @running?
        (>! upload-queue @generation)
        (<! (timeout 1000))))

  (go (while true
        (let [item (<! upload-queue)]
          (send-message {:best @current-best} sock)))))

;; Communication
(defn send-message [m sock] (ws/send sock m fmt/edn))

(defn handle-message [m sock]
  (cond
    (:start m)  (if-not @running? (do (run-async) (run-upload-async sock)))
    (:stop m)   (if @running? (stop-async))
    (:update m) (reset! ratings (:update m))))

(def comm-handlers {:on-message #(handle-message (read-string (.-data %)) socket)
                    :on-open    #(reset! online? true)
                    :on-close   #(reset! online? false)})
(def socket (ws/create @server-addr comm-handlers))

;; ---------------
;; View components

(defn update-mutation-rate [e]
  (reset! mutation-rate (/ (int (.. e -target -value)) 100)))
(defn render-mutation-rate-slider []
  (v/render-slider (* 100 @mutation-rate) 0 100 update-mutation-rate))

(defn update-elitism-rate [e]
  (reset! elitism-rate (/ (int (.. e -target -value)) 100)))
(defn render-elitism-rate-slider []
  (v/render-slider (* 100 @elitism-rate) 0 100 update-elitism-rate))

(defn render-prob-map-item [pmap-atom, func, name]
  (let [prob (@pmap-atom func)]
    [:div
     [:span name " " prob]
     (v/render-slider prob 1 100
                    (fn [e] (swap! pmap-atom assoc func (int (.. e -target -value)))))]))

(defn render-dashboard []
    [:div.row
     [:div.col-7
      [:div.card
       [:div.card-body
        [:span "Elitism rate " (Math/floor (* 100 @elitism-rate)) "%"]
        [render-elitism-rate-slider]

        [:h5.mt-4.mb-3 "Selection"]
        [render-prob-map-item selections-map s/tournament-selection "Tournament selection"]
        [render-prob-map-item selections-map s/random-selection "Random selection"]

        [:h5.mt-4.mb-3 "Mutation"]
        [:span "Mutation rate " (Math/floor (* 100 @mutation-rate)) "%"]
        [render-mutation-rate-slider]

        [:h6.mt-4.mb-3 "Types of mutation"]
        [render-prob-map-item mutations-map m/mut-swap "Random swap"]
        [render-prob-map-item mutations-map m/mut-shift "Random shift"]
        [render-prob-map-item mutations-map m/mut-reverse "Reverse genome"]]]]

     [:div.col-5
      (if @admin?
        [v/render-admin socket send-message])
      [:h5.mb-3 "Rating"]
      [:ol.pl-3
       (doall (map (fn [x]
                     ^{:key (first x)}
                     [:li.small {:class (if (= (first x) @username) "font-weight-bold" "")}
                      (str (first x) " " (Math/abs (second x)))])
                   (filter #(not (nil? (first %))) (h/sort-by-value-desc @ratings))))]]])

(defn app []
  [:div
   [v/render-status-stripe @online? @running?]

   [:div.container-fluid.container-degas..mt-3
    [:div.row
     [:div.col.pr-0
      [:div.evol-status
       [:h3.mb-0
        "Generation " @generation
        [:br]
        (Math/abs (fitness-tsm @current-best)) " km"
        ]
       [:div.small.mt-3 "Historical best: " (Math/abs (fitness-tsm @best)) " km."]]
      [:canvas {:id "themap", :width "630", :height "720"
                :class (if (> @elitism-rate 0.9) "elited")
                } "Map of The Netherlands"]]

     [:div.col
      (if @online?
        (if-not @authenticated?
          [:div.card.mb-3.bg-light
           [:div.card-body
            [v/render-name-input username admin? authenticated? socket send-message]]])
        [:h3.text-center.my-4 "Offline :'("])

      [render-dashboard]]]]])

;; Reagent stuff
(reagent/render-component [app] (. js/document (getElementById "app")))
(defn on-js-reload [])

;; ||||||||||||||||||||
;; Destroyers of worlds
(defn randomize-population! []
  (reset! population (into [] (sort-by fitness-tsm
                                       (p/rand-pop popsize indlength i/rand-unique-ind)))))

(defn reset-state! []
  (stop-async)
  (randomize-population!)
  (reset! authenticated? false)
  (reset! username "")
  (reset! best (first @population))
  (reset! current-best (first @population))
  (reset! generation 0))

;; (reset-state!)
(randomize-population!)
(reset! best (first @population))
(reset! current-best (first @population))
(reset! generation 0)

;; Canvas
(def canvas (.getElementById js/document "themap"))
(def ctx (.getContext canvas "2d"))
(set! (.-fillStyle ctx) "navy")
(.transform ctx 1, 0, 0, -1, 0, 720)

(doall (map #(dr/draw-city ctx (first %) (second %)) cities))
(dr/draw-pahts ctx cities (h/pathify @current-best))
