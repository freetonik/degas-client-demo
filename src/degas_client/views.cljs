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
  [:form
   {:on-submit (fn [e]
                 (.preventDefault e)
                 (reset! authenticated? true)
                 (if (= @username "vn3iguajdjitvnaohgioax")
                   (reset! admin true)
                   (send-fn {:name @username} socket)
                   ))}

   [:div.form-group.mb-0
    [:label "Your name"]
    [:input {:type "text"
             :class "form-control"
             :value @username
             :on-change #(reset! username (-> % .-target .-value))}]
    [:button {:type "submit"
              :class "btn btn-primary mt-3"}
     "Join the contest"
     ]]])

(defn render-admin [socket send-fn]
  [:div.card.mb-4
   [:div.card-body.d-flex.flex-row.justify-content-between
    [:input {:type "button" :value "GO"
                  :class "btn btn-success btn-sm"
                  :on-click (fn []
                              (send-fn {:admin-start true} socket))}]
    [:input {:type "button" :value "STOP"
                  :class "btn btn-warning btn-sm"
                  :on-click (fn []
                              (send-fn {:admin-stop true} socket))}]
    [:input {:type "button" :value "X"
                  :class "btn btn-danger btn-sm"
                  :on-click (fn []
                              (send-fn {:admin-reset true} socket))}]]])

(defn render-server-addr-input [value]
  [:input {:type "text"
           :value @value
           :on-change #(reset! value (-> % .-target .-value))}])
