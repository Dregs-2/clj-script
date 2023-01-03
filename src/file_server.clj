#! /usr/bin/env inlein
'{:dependencies [[org.clojure/clojure "1.11.1"] [io.vertx/vertx-web "4.3.4"]]}

(import (io.vertx.ext.web Router Route))
(import (io.vertx.ext.web.handler StaticHandler FileSystemAccess))
(import (io.vertx.core Future Handler Vertx))
(import (io.vertx.core.http HttpServer))

(def args-map (apply array-map (map #(try (load-string %) (catch Exception _ %)) *command-line-args*)))
(def port (:port args-map 52001))
(def root (:root args-map false))
(def path (:path args-map "./"))

(let [^Vertx vertx (Vertx/vertx)
      ^StaticHandler static-handle (StaticHandler/create (if root FileSystemAccess/ROOT FileSystemAccess/RELATIVE) path)
      ^Router router (Router/router vertx)
      ^HttpServer http-server (.createHttpServer vertx)]
  (.. router (route) (handler static-handle))
  (let [^Future future (.. http-server (requestHandler router) (listen port))]
    (.onSuccess future (reify Handler (handle [this _] (println (format "file server listen on port %s" port)))))
    (.onFailure future (reify Handler (handle [this _] (throw _))))
    ))
