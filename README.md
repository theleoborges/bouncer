# bouncer

A tiny Clojure library for validating maps (or records).

## Motivation

Check [this blog post](http://www.google.com) where I explain in detail the motivation behind this library

## Usage

If you're using leiningen, add it as a dependency to your project:

```clojure
[bouncer "0.1.0-SNAPSHOT"]
```

Or if you're using maven: 

```xml
<dependency>
  <groupId>bouncer</groupId>
  <artifactId>bouncer</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

*bouncer* provides two main functions, `validate` and `valid?`

`valid?` is a convenience function built on top of `validate`:

```clojure
(b/valid? {:name nil}
    (b/required [:name]))
;; true
```

`validate` takes a map and one or more validation functions - the library ships with a couple - and returns a vector. 

The first element in this vector contains a map of the error messages, whereas the second element contains the original map, augmented with the error messages.

Let's look at a few examples:

### Basic validations

Below is an example where we're validating that a given map has a value for both the keys `:name` and `age`.


```clojure
(require '[bouncer.core :as b])

(def person {:name "Leo"})

(b/validate person
    (b/required :name)
    (b/required :age))

;; [{:age ("age must be present")} 
;;  {:name "Leo", :errors {:age ("age must be present")}}]
```

As you can see, since age is missing, it's listed in the errors map with the appropriate error messages.

Error messages can be customized - e.g: in case you need them internationalized: 

```clojure
(b/validate person
    (b/required :age "Idade é um atributo obrigatório"))

;; [{:age ("Idade é um atributo obrigatório")} 
;;  {:name "Leo", :errors {:age ("Idade é um atributo obrigatório")}}]
```

### Validating nested maps

Nested maps can easily be validated as well, using the built-in validators:

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

### Multiple validation errors

If any of the entries fails more than one validation, all error messages are returned in a list like so:

```clojure
(b/validate person-1
    (b/required [:address :street])
    (b/number   [:address :postcode])
    (b/positive   [:address :postcode]))

;;[{:address {:postcode ("postcode must be a positive number" "postcode must be a number"), 
;;  :street ("street must be present")}} 
;;
;;  {:errors {:address {:postcode ("postcode must be a positive number" "postcode must be a number"), ;;   :street ("street must be present")}}, 
;;     :address {:country "Brazil", :postcode "invalid", :street nil}}]
```
The error map now contains the path `[:address :postcode]`, which is a list with all validation errors for that entry.

### Validating collections

Sometimes it's useful to perform simple, ad-hoc checks in collections contained within a map. For that purpose, *bouncer* provides `every`. 

As with the other validators, it takes a key (or a vector) as the first argument. This time however, the value in the given key/path must be a collection (vector, list etc...)

`every` also takes a predicate function as its second argument. It will be invoked for every item in the collection, making sure they all pass.

Let's see it in action:

```clojure
(def person-with-pets {:name "Leo" 
                       :pets [{:name nil}
                              {:name "Gandalf"}]})

(core/validate person-with-pets
          (core/every :pets #(not (nil? (:name %)))))

;;[{:pets ("All items in pets must satisfy the predicate")} 
;; {:name "Leo", :pets [{:name nil} {:name "Gandalf"}], 
;; :errors {:pets ("All items in pets must satisfy the predicate")}}]
```



## Built-in validations

*bouncer* was a couple night's work so it only ships with the validations I've needed myself. At the moment they live in the core namespace:

- `bouncer.core/required`

- `bouncer.core/number`

- `bouncer.core/positive`

- `bouncer.core/every` (for ad-hoc validation of collections)

Pull requests with more validators are welcome - if you wish to contribute, make sure you read the next section.

## Writing custom validators

The `bouncer.core` namespace includes a function called `mk-validator`. It is responsible for turning a predicate, key and an optional message into a valid validating function.

As such, your custom validators will probably want to call it. From its docstring:

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
(defn my-number-validator [k]
    (b/mk-validator number? k (format "%s must be a number" (name k)) :optional true))
```

You can see its usage is fairly simple. 

First we create a function that receives the key in with we'll find the value we would like to make sure is a number.

Then we call `mk-validator` with the following arguments:

- the predicate function `number?` from Clojure core;
- the key `k` received as an argument;
- a message to be added to the errors map in case the validation fails;
- and we set the optional argument to true, making sure this validation only runs if the key has a value in the map

Using it is then straightforward:

```clojure
(b/validate {:postcode "NaN"}
    (my-number-validator :postcode))

;; [{:postcode ("postcode must be a number")} 
;;  {:errors {:postcode ("postcode must be a number")}, :postcode "NaN"}]
```

Feedback to both this library and this guide is welcome.

## License

Copyright © 2012 [Leonardo Borges](http://www.leonardoborges.com)

Distributed under the [MIT License](http://opensource.org/licenses/MIT).
