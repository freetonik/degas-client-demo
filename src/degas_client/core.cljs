(ns degas-client.core
  (:require [reagent.core :as reagent :refer [atom]]
            [wscljs.client :as ws]
            [wscljs.format :as fmt]
            [clojure.core.async :refer [>! <! go chan timeout]]
            [cljs.reader :refer [read-string]]
            [degas-client.tsm :refer [distances get-distance fitness-tsm]]
            [degas.individual :as i]
            [degas.crossover :as c]
            [degas.mutation :as m]
            [degas.population :as p]
            [degas.selection :as s]
            [degas.evolution :as e]
            [degas.fitness :as f]
            [degas.helpers :as h]))

(enable-console-print!)

(declare comm-handlers)
(declare send-message)
(declare socket)
(def server-addr "ws://localhost:3449")

(def popsize   30)
(def indlength 50)

;; State
(defonce online? (atom false))
(defonce authenticated? (atom false))
(defonce ratings (atom {}))
(defonce username (atom ""))
(defonce generation (atom 0))
(defonce population (atom []))
(defonce current-best (atom (first @population)))
(defonce best (atom (first @population)))
(defonce mutation-rate (/ (rand-int 40) 100))
(defonce elitism-rate (/ (rand-int 8) 100))

(defonce selections-map (atom { s/tournament-selection 1, s/random-selection 1 }))
(defonce mutations-map (atom { m/mut-swap 1, m/mut-shift 1}))

;; Async state
(defonce running? (atom false))
(def queue (chan 10))
(def upload-queue (chan 10))

;; Evolution
(defn update-best []
  (if (> (fitness-tsm @current-best) (fitness-tsm @best))
    (reset! best @current-best)))

(defn evolve-step []
  (reset! population
          (sort-by fitness-tsm
                   (e/next-generation @population
                                      fitness-tsm
                                      @selections-map
                                      { c/crossover-ordered 1 }
                                      @mutations-map
                                      @mutation-rate
                                      @elitism-rate))))

;; Async evolution
(defn stop-async [] (reset! running? false))

(defn run-async []
  (reset! running? true)
  ;; (reset! running? false)

  (go (while @running?
        (>! queue @generation)
        (<! (timeout 100))))
  ;; (go (>! queue @generation))

  (go (while true
        (let [item (<! queue)]
          (evolve-step)
          (reset! current-best (first @population))
          (update-best)
          (swap! generation inc))))
  )

;; (run-async)
;; (stop-async)

;; Upload
(defn upload-best [ind sock]
  (send-message {:best ind} sock))

(defn run-upload-async [sock]
  (go (while @running?
        (>! upload-queue @generation)
        (<! (timeout 1000))))

  (go (while true
        (let [item (<! upload-queue)]
          (upload-best @best sock)
          ))))

;; Communication
(defn send-message [m sock] (ws/send sock m fmt/edn))

(defn handle-message [m sock]
  (cond
    (:start m) (if-not @running? (do (run-async)
                                     (run-upload-async sock)))
    (:stop m)  (if @running?    (stop-async))

    (:update m) (reset! ratings (:update m))
    ))

(def comm-handlers {:on-message #(handle-message (read-string (.-data %)) socket)
                    :on-open    #(reset! online? true)
                    :on-close   #(reset! online? false)})
(def socket (ws/create server-addr comm-handlers))

;; ---------------
;; View components
(defn render-run-button []
  (if @running?
    [:button.btn.btn-success.mr-3 {:on-click stop-async} "Pause"]
    [:button.btn.btn-success.mr-3 {:on-click run-async} "Run"]))

(defn render-slider [value min max callback]
  [:input {:type "range" :value value :min min :max max
           :style {:width "100%"}
           :on-change callback}])

(defn render-name-input []
  [:div
   [:input {:type "text"
            :value @username
            :on-change #(reset! username (-> % .-target .-value))}]
   [:input {:type "button" :value "Enroll!"
            :class "btn btn-success"
            :on-click (fn []
                        (reset! authenticated? true)
                        (send-message {:name @username} socket))}]])

(defn update-mutation-rate [e]
  (reset! mutation-rate (/ (int (.. e -target -value)) 100)))
(defn render-mutation-rate-slider []
  (render-slider (* 100 @mutation-rate) 0 100 update-mutation-rate))

(defn update-elitism-rate [e]
  (reset! elitism-rate (/ (int (.. e -target -value)) 100)))
(defn render-elitism-rate-slider []
  (render-slider (* 100 @elitism-rate) 0 100 update-elitism-rate))

(defn render-ratings [r]
  [:ol.pl-3.ml-1
   (doall (map (fn [x]
                 ^{:key (first x)}
                 [:li {:class (if (= (first x) @username) "font-weight-bold" "")}
                  (str (first x) " " (Math/abs (second x)))])
               (filter #(not= (first %) nil) (h/sort-by-value-desc @ratings))))])

(defn render-control-panel []
  [:div.card
   [:div.card-header "God mode"]
   [:div.card-body
    [:div.row

     [:div.col
      [:span "Mutation rate " (Math/floor (* 100 @mutation-rate)) "%"]
      [render-mutation-rate-slider]]

     [:div.col
      [:span "Elitism rate " (Math/floor (* 100 @elitism-rate)) "%"]
      [render-elitism-rate-slider]]]


     ]]
  )

(defn render-evol-status []
  [:div.card
   [:div.card-header "Status"]
   [:div.card-body
    [:div
     [:h5 "Generation " @generation]

     [:p
      (prn-str @current-best)
      "  --->  "
      (prn-str (fitness-tsm @current-best))]

     [:h5 "Best so far:"]
     [:p
      (prn-str @best)
      "  --->  "
      [:strong (prn-str (fitness-tsm @best))]]]]]
  )

(defn render-status-stripe [connected? computing?]
  [:div.progress.rounded-0
   {:style {:height "7px"}}
   [:div.progress-bar
    {:class (if connected?
              (if computing? "bg-success progress-bar-animated progress-bar-striped"
                             "bg-success")
              "bg-warning")
     :style {:width "100%", :height "7px"}}]])

(defn render-dashboard []
    [:div.row
     [:div.col-9
      [:h3.mb-4 "The World of " [:b @username]]
      [render-control-panel]
      [:div.mt-4]
      [render-evol-status]
      ]

     [:div.col-3.border-left
      [:h4.mb-3 "Global ratings"]
      [render-ratings @ratings]
      ]
    ])

(defn render-auth []
  [:div.row.justify-content-center
   [:div.col-4 [render-name-input]]])

(defn render-offline-status []
  [:div
   [:h3.text-center.mt-4 "Offline :'("]])

(defn app []
  [:div
   [render-status-stripe @online? @running?]

   [:div.container-fluid.mt-3
    (if @online?
      (if @authenticated?
        [render-dashboard]
        [render-auth])
      [render-offline-status])]])

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
