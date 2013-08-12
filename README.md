# bouncer [![Travis CI status](https://api.travis-ci.org/leonardoborges/bouncer.png)](http://travis-ci.org/#!/leonardoborges/bouncer/builds)

A validation DSL for Clojure apps

## Table of Contents

* [Annotated Source](http://leonardoborges.github.com/bouncer/)
* [Motivation](#motivation)
* [Setup](#setup)
* [Usage](#usage)
    * [Basic validations](#basic-validations)
    * [Validating nested maps](#validating-nested-maps)
    * [Multiple validation errors](#multiple-validation-errors)
    * [Validating collections](#validating-collections)
    * [Validation pipelining](#validation-pipelining)    
    * [Pre-conditions](#pre-conditions)    
* [Validator sets](#validator-sets)
* [Customization support](#customization-support)
    * [Custom validators using arbitrary functions](#custom-validations-using-arbitrary-functions)
    * [Writing validators](#writing-validators)
        * [Validators and arbitrary number of arguments](#validators-and-arbitrary-number-of-arguments)
* [Built-in validators](#built-in-validations)
* [Contributing](#contributing)
* [TODO](#todo)
* [CHANGELOG](https://github.com/leonardoborges/bouncer/blob/master/CHANGELOG.md)
* [CONTRIBUTORS](#contributors)
* [License](#license)

## Motivation

Check [this blog post](http://www.leonardoborges.com/writings/2013/01/04/bouncer-validation-lib-for-clojure/) where I explain in detail the motivation behind this library

## Setup

If you're using leiningen, add it as a dependency to your project:

```clojure
[bouncer "0.2.3-beta4"]
```

Or if you're using maven:

```xml
<dependency>
  <groupId>bouncer</groupId>
  <artifactId>bouncer</artifactId>
  <version>0.2.3-beta4</version>
</dependency>
```

Then, require the library:

```clojure
(require '[bouncer [core :as b] [validators :as v]])
```

*bouncer* provides two main macros, `validate` and `valid?`

`valid?` is a convenience function built on top of `validate`:

```clojure
(b/valid? {:name nil}
    :name v/required)

;; false
```

`validate` takes a map and one or more validation forms and returns a vector.

The first element in this vector contains a map of the error messages, whereas the second element contains the original map, augmented with the error messages.

Let's look at a few examples:

## Usage

### Basic validations

Below is an example where we're validating that a given map has a value for both the keys `:name` and `:age`.


```clojure
(require '[bouncer [core :as b] [validators :as v]])

(def person {:name "Leo"})

(b/validate person
    :name v/required
    :age  v/required)

;; [{:age ("age must be present")} 
;;  {:name "Leo", :bouncer.core/errors {:age ("age must be present")}}]
```

As you can see, since age is missing, it's listed in the errors map with the appropriate error messages.

Error messages can be customized by providing a `:message` option - e.g: in case you need them internationalized:

```clojure
(b/validate person
    :age [[v/required :message "Idade é um atributo obrigatório"]])

;; [{:age ("Idade é um atributo obrigatório")} 
;;  {:name "Leo", :bouncer.core/errors {:age ("Idade é um atributo obrigatório")}}]
```

Note the double vector: 

- the inner one wraps a single validation where the first element is the validating function and the rest are options for that validation.
- the outer vector simply denotes a list of validations to be applied

### Validating nested maps

Nested maps can easily be validated as well, using the built-in validators:

```clojure
(def person-1
    {:address
        {:street nil
         :country "Brazil"
         :postcode "invalid"
         :phone "foobar"}})

(b/validate person-1
    [:address :street]   v/required
    [:address :postcode] v/number
    [:address :phone]    [[v/matches #"^\d+$"]])


;;[{:address 
;;              {:phone ("phone must match the given regex pattern"), 
;;               :postcode ("postcode must be a number"), 
;;               :street ("street must be present")}} 
;;   {:bouncer.core/errors {:address {
;;                          :phone ("phone must match the given regex pattern"), 
;;                          :postcode ("postcode must be a number"), 
;;                          :street ("street must be present")}}, 
;;                          :address {:country "Brazil", :postcode "invalid", :street nil, 
;;                          :phone "foobar"}}]
```

In the example above, the vector of keys is assumed to be the path in an associative structure.

### Multiple validation errors

`bouncer` features a short circuit mechanism for multiple validations within a single field.

For instance, say you're validating a map representing a person and you expect the key `:age` to be `required`, a `number` and also be `positive`:


```clojure
(b/validate {:age nil}
    :age [v/required v/number v/positive])
    
;; [{:age ("age must be present")} {:bouncer.core/errors {:age ("age must be present")}, :age nil}]
```

As you can see, only the `required` validator was executed. That's what I meant by the short circuit mechanism. As soon as a validation fails, it exits and returns that error, skipping further validators.

However, note this is true within a single map entry. Multiple map entries will have all its messages returned as expected:

```clojure
(b/validate person-1
    [:address :street] v/required
    [:address :postcode] [v/number v/positive])

;; [{:address {:postcode ("postcode must be a number"), :street ("street must be present")}} {:bouncer.core/errors {:address {:postcode ("postcode must be a number"), :street ("street must be present")}}, :address {:country "Brazil", :postcode "invalid", :street nil, :phone "foobar"}}]
```

Also note that if we need multiple validations against any keyword or path, we need only provide them inside a vector, like `[v/number v/positive]` above.

### Validating collections

Sometimes it's useful to perform simple, ad-hoc checks in collections contained within a map. For that purpose, *bouncer* provides `every`.

Its usage is similar to the validators seen so far. This time however, the value in the given key/path must be a collection (vector, list etc...)

Let's see it in action:

```clojure
(def person-with-pets {:name "Leo"
                       :pets [{:name nil}
                              {:name "Gandalf"}]})

(b/validate person-with-pets
          :pets [[v/every #(not (nil? (:name %)))]])

;;[{:pets ("All items in pets must satisfy the predicate")} 
;; {:name "Leo", :pets [{:name nil} {:name "Gandalf"}], 
;; :bouncer.core/errors {:pets ("All items in pets must satisfy the predicate")}}]
```

All we need to do is provide a predicate function to `every`. It will be invoked for every item in the collection, making sure they all pass.

### Validation pipelining

Note that if a map if pipelined through multiple validators, bouncer will leave it's errors map untouched and simply add new validation errors to it:

```clojure
(-> {:age "NaN"}
    (b/validate :name v/required)
    second
    (b/validate :age v/number)
    second
    ::b/errors)
    
;; {:age ("age must be a number"), :name ("name must be present")}
```

### Pre-conditions

Validators can take a pre-condition option `:pre` that causes it to be executed only if the given pre-condition - a truthy function - is met.

Consider the following:

```clojure
(b/valid? {:a -1 :b "X"}
           :b [[v/member #{"Y" "Z"} :pre (comp pos? :a)]])
             
;; true
```

As you can see the value of `b` is clearly not in the set `#{"Y" "Z"}`, however the whole validation passes because the `v/member` check states is should only be run if `:a` is positive.

Let's now make it fail:

```clojure
(b/valid? {:a 1 :b "X"}
           :b [[v/member #{"Y" "Z"} :pre (comp pos? :a)]])
             
;; false
```


## Validator sets

If you find yourself repeating a set of validators over and over, chances are you will want to group and compose them somehow. Validator sets are simply plain Clojure maps:


```clojure

;; first we define the set of validators we want to use
(def address-validations
  {:postcode [v/required v/number]
   :street    v/required
   :country   v/required})

;;just something to validate
(def person {:address {
                :postcode ""
                :country "Brazil"}})

;;now we compose the validators
(b/validate person
            :name    v/required
            :address address-validations)

;;[{:address 
;;    {:postcode ("postcode must be a number" "postcode must be present"), 
;;     :street ("street must be present")}, 
;;     :name ("name must be present")} 
;; 
;; {:bouncer.core/errors {:address {:postcode ("postcode must be a number" "postcode must be present"), 
;;  :street ("street must be present")}, :name ("name must be present")}, 
;;  :address {:country "Brazil", :postcode ""}}]
```

You can also compose validator sets together and use them as top level validations:


```clojure
(def address-validator
  {:postcode v/required})

(def person-validator
  {:name v/required
   :age [v/required v/number]
   :address address-validator})
  
(b/validate {}
			person-validator)
			   
;;[{:address {:postcode ("postcode must be present")}, :age ("age must be present"), :name ("name must be present")} {:bouncer.core/errors {:address {:postcode ("postcode must be present")}, :age ("age must be present"), :name ("name must be present")}}]
```

## Customization Support

### Custom validations using plain functions

Using your own functions as validators is simple:

```clojure
(defn young? [age]
    (< age 25))

(b/validate {:age 29}
            :age [[young? :message "Too old!"]])

;; [{:age ("Too old!")} 
;;  {:bouncer.core/errors {:age ("Too old!")}, :age 29}]
```

### Writing validators

As shown above, validators as just functions. The downside is that by using a function bouncer will default to a validation message that might not make sense in a given scenario:

```clojure
(b/validate {:age 29}
               :age young?)
               
;; [{:age ("Custom validation failed for age")} 
;; {:bouncer.core/errors {:age ("Custom validation failed for age")}, :age 29}]
```

You could of course use the message keyword as in previous examples but if you reuse the validation in several places, you'd need a lot of copying and pasting.

Another way - and the preferred one - to provide custom validations is to use the macro `defvalidator` in the `bouncer.validators` namespace.

The advantage of this approach is that it attaches the needed metadata for bouncer to know which message to use:

As an example, here's a simplified version of the `bouncer.validators/number` validator:

```clojure
(use '[bouncer.validators :only [defvalidator]])

(defvalidator my-number-validator
  {:default-message-format "%s must be a number"}
  [maybe-a-number]
  (number? maybe-a-number))
```

`defvalidator` takes your validator name, an optional map of options and the body of your predicate function.

Options is a map of key/value pairs where:

- `:default-message-format` - to be used when clients of this validator don't provide one
- `:optional` - a boolean indicating if this validator should only trigger for keys that have a value different than `nil`. Defaults to false.

That's all syntactic sugar for:

```clojure
(def my-number-validator
  (with-meta (fn my-number-validator
               ([maybe-a-number]
                  (number? maybe-a-number)))
    {:default-message-format "%s must be a number", :optional false}))
```

Using it is then straightforward:

```clojure
(b/validate {:postcode "NaN"}
          :postcode my-number-validator)


;; [{:postcode ("postcode must be a number")} 
;;  {:bouncer.core/errors {:postcode ("postcode must be a number")}, :postcode "NaN"}]
```

As you'd expect, the message can be customized as well:

```clojure
(b/validate {:postcode "NaN"}
          :postcode [[my-number-validator :message "must be a number"]])
```

### Validators and arbitrary number of arguments

Your validators aren't limited to a single argument though.

Since *v0.2.2*, `defvalidator` takes an arbitrary number of arguments. The only thing you need to be aware is that the value being validated will **always** be the first argument you list - this applies if you're using plain functions too. Let's see an example with the `member` validator:

```clojure
(defvalidator member
  [value coll]
  (some #{value} coll))
```

Yup, it's that *simple*. Let's use it:

```clojure
(def kid {:age 10})

(b/validate kid
            :age [[member (range 5)]])
```

In the example above, the validator will be called with `10` - that's the value the key `:age` holds - and `(0 1 2 3 4)` - which is the result of `(range 5)` and will be fed as the second argument to the validator.

## Built-in validations

I didn't spend a whole lot of time on *bouncer* so it only ships with the validations I've needed myself. At the moment they live in the validators namespace:

- `bouncer.validators/required`

- `bouncer.validators/number`

- `bouncer.validators/positive`

- `bouncer.validators/member`

- `bouncer.validators/matches` (for matching regular expressions)

- `bouncer.validators/every` (for ad-hoc validation of collections. All items must match the provided predicate)

## Contributing

Pull requests of bug fixes and new validators are most welcome.

Note that if you wish your validator to be merged and considered *built-in* you must implement it using the macro `defvalidator` shown above.

Feedback to both this library and this guide is welcome.

## TODO

- Add more validators (help is appreciated here)
- Docs are getting a bit messy. Fix that.

## CONTRIBUTORS

- [leonardoborges](https://github.com/leonardoborges)
- [ghoseb](https://github.com/ghoseb)

## License

Copyright © 2012-2013 [Leonardo Borges](http://www.leonardoborges.com)

Distributed under the [MIT License](http://opensource.org/licenses/MIT).
