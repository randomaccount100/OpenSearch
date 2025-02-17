/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/*
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

package org.opensearch.action.admin.indices.settings.get;

import org.opensearch.LegacyESVersion;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.action.IndicesRequest;
import org.opensearch.action.ValidateActions;
import org.opensearch.action.support.IndicesOptions;
import org.opensearch.action.support.master.MasterNodeReadRequest;
import org.opensearch.common.Strings;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

public class GetSettingsRequest extends MasterNodeReadRequest<GetSettingsRequest> implements IndicesRequest.Replaceable {

    private String[] indices = Strings.EMPTY_ARRAY;
    private IndicesOptions indicesOptions = IndicesOptions.fromOptions(false, true, true, true);
    private String[] names = Strings.EMPTY_ARRAY;
    private boolean humanReadable = false;
    private boolean includeDefaults = false;

    @Override
    public GetSettingsRequest indices(String... indices) {
        this.indices = indices;
        return this;
    }

    public GetSettingsRequest indicesOptions(IndicesOptions indicesOptions) {
        this.indicesOptions = indicesOptions;
        return this;
    }

    /**
     * When include_defaults is set, return default values which are normally suppressed.
     * This flag is specific to the rest client.
     */
    public GetSettingsRequest includeDefaults(boolean includeDefaults) {
        this.includeDefaults = includeDefaults;
        return this;
    }

    public GetSettingsRequest() {
    }

    public GetSettingsRequest(StreamInput in) throws IOException {
        super(in);
        indices = in.readStringArray();
        indicesOptions = IndicesOptions.readIndicesOptions(in);
        names = in.readStringArray();
        humanReadable = in.readBoolean();
        if (in.getVersion().onOrAfter(LegacyESVersion.V_6_4_0)) {
            includeDefaults = in.readBoolean();
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeStringArray(indices);
        indicesOptions.writeIndicesOptions(out);
        out.writeStringArray(names);
        out.writeBoolean(humanReadable);
        if (out.getVersion().onOrAfter(LegacyESVersion.V_6_4_0)) {
            out.writeBoolean(includeDefaults);
        }
    }

    @Override
    public String[] indices() {
        return indices;
    }

    @Override
    public IndicesOptions indicesOptions() {
        return indicesOptions;
    }

    @Override
    public boolean includeDataStreams() {
        return true;
    }

    public String[] names() {
        return names;
    }

    public GetSettingsRequest names(String... names) {
        this.names = names;
        return this;
    }

    public boolean humanReadable() {
        return humanReadable;
    }

    public GetSettingsRequest humanReadable(boolean humanReadable) {
        this.humanReadable = humanReadable;
        return this;
    }

    public boolean includeDefaults() {
        return includeDefaults;
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException validationException = null;
        if (names == null) {
            validationException = ValidateActions.addValidationError("names may not be null", validationException);
        }
        return validationException;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GetSettingsRequest that = (GetSettingsRequest) o;
        return humanReadable == that.humanReadable &&
            includeDefaults == that.includeDefaults &&
            Arrays.equals(indices, that.indices) &&
            Objects.equals(indicesOptions, that.indicesOptions) &&
            Arrays.equals(names, that.names);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(indicesOptions, humanReadable, includeDefaults);
        result = 31 * result + Arrays.hashCode(indices);
        result = 31 * result + Arrays.hashCode(names);
        return result;
    }
}
