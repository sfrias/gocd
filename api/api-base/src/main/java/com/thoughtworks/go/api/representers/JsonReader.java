/*
 * Copyright 2018 ThoughtWorks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.thoughtworks.go.api.representers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Optional;

import static com.thoughtworks.go.api.util.HaltResponses.haltBecauseInvalidJSON;
import static java.lang.String.format;

public class JsonReader {

    private final JsonObject jsonObject;

    public JsonReader(JsonObject jsonObject) {
        this.jsonObject = jsonObject;
    }

    public String getString(String property) {
        return optString(property)
                .orElseThrow(() -> haltBecauseInvalidJSON(format("Json does not contain property: %s", property)));
    }

    public Optional<String> optString(String property) {
        if (jsonObject.has(property)) {
            try {
                return Optional.ofNullable(jsonObject.get(property).getAsString());
            } catch (Exception e) {
                throw haltBecauseInvalidJSON(format("Could not get %s as a String", property));
            }
        }
        return Optional.empty();
    }

    public Optional<JsonArray> optJsonArray(String property) {
        if (jsonObject.has(property)) {
            try {
                return Optional.ofNullable(jsonObject.getAsJsonArray(property));
            } catch (Exception e) {
                throw haltBecauseInvalidJSON(format("Could not get %s as a JsonArray", property));
            }
        }
        return Optional.empty();
    }

    public Optional<JsonReader> optJsonObject(String property) {
        if (jsonObject.has(property)) {
            try {
                return Optional.of(new JsonReader(jsonObject.getAsJsonObject(property)));
            } catch (Exception e) {
                throw haltBecauseInvalidJSON(format("Could not get %s as a JsonObject", property));
            }
        }
        return Optional.empty();
    }

    public JsonReader readJsonObject(String property) {
        return optJsonObject(property)
                .orElseThrow(() -> haltBecauseInvalidJSON(format("Json does not contain property: %s", property)));
    }
}
