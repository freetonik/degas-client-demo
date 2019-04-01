(ns degas.evolution
  (:require [degas.helpers :as h]
            [degas.selection :as s ]))

(defn next-generation-old [population fitness-fn selection-fn selection-prob crossover-fn mutation-fn]
  "Returns a new population."
  (selection-fn population selection-prob fitness-fn)
  )


(defn next-generation
  [pop fitness-fn selection-fn crossovers-map mutations-map elitism-ratio]
  (let [
        popsize       (count pop)
        crossover-fn  (h/weighted-rand-choice crossovers-map)
        mutation-fn   (h/weighted-rand-choice mutations-map)
        elite-count   (Math/floor (* elitism-ratio popsize))
        newpop        (s/top-n pop elite-count fitness-fn)
        offspring-count (- popsize elite-count)
        breedable-pop (into [] (take-last offspring-count pop))
        ]

    (into newpop (repeatedly offspring-count
                             #(apply crossover-fn
                                     (selection-fn breedable-pop fitness-fn))))))
