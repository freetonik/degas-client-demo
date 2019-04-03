(ns degas-client.drawing)

(defn draw-city [ctx x y]
  (.beginPath ctx)
  (.arc ctx x y 4 0 (* Math/PI 2))
  (.fill ctx))

(defn draw-line [ctx x1 y1 x2 y2]
  (.beginPath ctx)
  (.moveTo ctx x1 y1)
  (.lineTo ctx x2 y2)
  (.stroke ctx))

(defn draw-connection [ctx c1 c2]
  (draw-line ctx (first c1) (second c1) (first c2) (second c2)))

(defn draw-pahts [ctx cities paths]
  (doall (map #(draw-connection ctx (cities (first %)) (cities (second %))) paths)))

(defn clear-canvas [ctx width height]
  (.clearRect ctx 0 0 width height))
