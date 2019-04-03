(ns degas-client.views)

(defn render-run-button [running? start-fn stop-fn]
  (if running?
    [:button.btn.btn-success.mr-3 {:on-click stop-fn} "Pause"]
    [:button.btn.btn-success.mr-3 {:on-click start-fn} "Run"]))

(defn render-status-stripe [connected? computing?]
  [:div.progress.rounded-0
   {:style {:height "5px"}}
   [:div.progress-bar
    {:class (if connected?
              (if computing? "bg-success progress-bar-animated progress-bar-striped"
                             "bg-success")
              "bg-warning")
     :style {:width "100%", :height "5px"}}]])

(defn render-slider [value min max callback]
  [:input {:type "range" :value value :min min :max max
           :style {:width "100%"} :on-change callback}])

(defn render-name-input [username admin authenticated? socket send-fn]
  [:div.text-center
   [:p "Enter your name"]
   [:input {:type "text"
            :value @username
            :on-change #(reset! username (-> % .-target .-value))}]
   [:br]
   [:input.mt-3 {:type "button" :value "Join the contest"
            :class "btn btn-success"
            :on-click (fn []
                        (reset! authenticated? true)
                        (if (= @username "admin")
                          (reset! admin true)
                          (send-fn {:name @username} socket)
                          )
                        )}]])

(defn render-admin [socket send-fn]
  [:div.card.mb-4
   [:div.card-body.d-flex.flex-row.justify-content-between
    [:input {:type "button" :value "GO"
                  :class "btn btn-success"
                  :on-click (fn []
                              (send-fn {:admin-start true} socket))}]
    [:input {:type "button" :value "STOP"
                  :class "btn btn-warning"
                  :on-click (fn []
                              (send-fn {:admin-stop true} socket))}]]])
