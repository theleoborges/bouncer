# bouncer

A tiny Clojure library for validating maps (or records).

## Motivation

Skip this section and go straight to *Usage* if you don't care about monads.

There are a couple of clojure validation libraries already out there so why would I write a new one? Well:

- Writing Clojure is fun!

- Because of Monads!

While deepening my knowledge about functional programming the inevitable subject of monads came about - [I wrote a tutorial on the topic in case you're interested](http://www.leonardoborges.com/writings/2012/11/30/monads-in-small-bites-part-i-functors/).

After learning what they are and thinking about the validation problem for a while, I couldn't help but notice that the problem had a lot in common with the [State Monad](http://www.haskell.org/haskellwiki/State_Monad) - namely the fact that I want, as the map is being validated, to collect all validation errors that happened on the way.

So I decided to leverage all of the machinery provided by the wonderful [algo.monads](https://github.com/clojure/algo.monads/) library and build *bouncer* on top of its [State Monad](https://github.com/clojure/algo.monads/blob/master/src/main/clojure/clojure/algo/monads.clj#L395).

In the end this might be useful as one more answer to the question *What are monads useful for?*

## Usage

*bouncer* provides two main functions, `validate` and `valid?`

`validate` takes a map and one or more validation functions - the library ships with a couple - and returns a vector. 

The first element in this vector contains a map of the error messages, whereas the second element contains the original map, augmented with the error messages.

Here's an example:

```clojure
(require '[bouncer.core :as b])

(def person {:name "Leo"})

(b/validate person
    (b/required :name)
    (b/required :age))

;; [{:age ("age must be present")} 
;;  {:name "Leo", :errors {:age ("age must be present")}}]
```

Error messages can be customized:

```clojure
(b/validate person
    (b/required :age "Idade é um atributo obrigatório"))

;; [{:age ("Idade é um atributo obrigatório")} 
;;  {:name "Leo", :errors {:age ("Idade é um atributo obrigatório")}}]
```

Nested maps can easily be validated:

```clojure
(def person-1 
    {:address 
        {:street nil 
         :country "Brazil"
         :postcode "invalid"}})

(b/validate person-1
    (b/required [:address :street])
    (b/number   [:address :postcode]))

;; [{:address {:postcode ("postcode must be a number"), :street ("street must be present")}} 
;;  {:errors {:address {:postcode ("postcode must be a number"), :street ("street must be present")}},
;;   :address {:country "Brazil", :postcode "invalid", :street nil}}]
```

`valid?` is a convenience function built on top of `validate`:

```clojure
(b/valid? person-1
    (b/required [:address :street])
    (b/number   [:address :postcode]))
;; false

(b/valid? person
    (b/required [:name]))
;; true
```

## Built-in validations

*bouncer* was a couple night's work so it only ships with the validations I've needed myself. At the moment they live in the core namespace:

- `bouncer.core/required`

- `bouncer.core/number`

- `bouncer.core/positive`

Pull requests with more validators are welcome - if you wish to contribute, make sure you read the next section.

## Writing custom validators

The `bouncer.core` namespace includes a function called `mk-validator`.

Your validation function will probably want to call it. From the docstring:

```clojure
-------------------------
bouncer.core/mk-validator
([pred k msg & {optional :optional}] [pred k msg])
  Returns a validation function that will use (pred (k m)) to determine if m is valid. msg will be added to the :errors entry in invalid scenarios.

  If k is a vector, it is assumed to be the path in an nested associative structure. In this case, the :errors entry will mirror this path

  A validator can also be marked optional, in which case the validation will only run if k has a value in m. e.g.:

  (mk-validator number? k msg :optional true)
```

As an example, here's a simplified version of the `bouncer.core/number` validator:

```clojure
(defn number
  ([k]
     (number k (format "%s must be a number" (name k))))
  ([k msg]
     (mk-validator number? k msg :optional true)))
```

## License

Copyright © 2012 [Leonardo Borges](http://www.leonardoborges.com)

Distributed under the [MIT License](http://opensource.org/licenses/MIT).
