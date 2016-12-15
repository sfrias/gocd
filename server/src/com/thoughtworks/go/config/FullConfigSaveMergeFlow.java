/*
 * Copyright 2016 ThoughtWorks, Inc.
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

package com.thoughtworks.go.config;

import com.thoughtworks.go.config.exceptions.ConfigMergePostValidationException;
import com.thoughtworks.go.config.exceptions.ConfigMergePreValidationException;
import com.thoughtworks.go.config.registry.ConfigElementImplementationRegistry;
import com.thoughtworks.go.config.remote.PartialConfig;
import com.thoughtworks.go.config.update.FullConfigUpdateCommand;
import com.thoughtworks.go.domain.GoConfigRevision;
import com.thoughtworks.go.server.util.ServerVersion;
import com.thoughtworks.go.service.ConfigRepository;
import com.thoughtworks.go.util.SystemEnvironment;
import com.thoughtworks.go.util.TimeProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

import static java.lang.String.format;

@Component
public class FullConfigSaveMergeFlow extends FullConfigSaveFlow{
    @Autowired
    public FullConfigSaveMergeFlow(ConfigCache configCache, ConfigElementImplementationRegistry configElementImplementationRegistry,
                                   SystemEnvironment systemEnvironment, ServerVersion serverVersion, TimeProvider timeProvider,
                                   ConfigRepository configRepository, CachedGoPartials cachedGoPartials) {
        this(new MagicalGoConfigXmlLoader(configCache, configElementImplementationRegistry),
                new MagicalGoConfigXmlWriter(configCache, configElementImplementationRegistry), configElementImplementationRegistry,
                serverVersion, timeProvider, configRepository, cachedGoPartials, new GoConfigFileWriter(systemEnvironment));
    }

    FullConfigSaveMergeFlow(MagicalGoConfigXmlLoader loader, MagicalGoConfigXmlWriter writer,
                            ConfigElementImplementationRegistry configElementImplementationRegistry,
                            ServerVersion serverVersion, TimeProvider timeProvider, ConfigRepository configRepository,
                            CachedGoPartials cachedGoPartials, GoConfigFileWriter fileWriter) {
        super(loader, writer, configElementImplementationRegistry, serverVersion, timeProvider, configRepository, cachedGoPartials, fileWriter);
    }

    public GoConfigHolder execute(FullConfigUpdateCommand updatingCommand, final List<PartialConfig> partials, String currentUser) throws Exception {
        LOGGER.debug("[Config Save] Starting Config Save using FullConfigSaveMergeFlow");

        CruiseConfig configForEdit = configForEditWithPartials(updatingCommand, partials);

        String configForEditXml = toXmlString(configForEdit, updatingCommand.unmodifiedMd5());

        String mergedConfig = getMergedConfig(configForEditXml, currentUser, updatingCommand.unmodifiedMd5());

        GoConfigHolder goConfigHolder = reloadConfig(mergedConfig, partials);

        checkinToConfigRepo(currentUser, goConfigHolder.configForEdit, mergedConfig);

        writeToConfigXml(mergedConfig);

        cachedGoPartials.markAsValid(partials);

        setMergedConfigForEditOn(goConfigHolder, partials);

        LOGGER.debug("[Config Save] Done Config Save using FullConfigSaveMergeFlow");

        return goConfigHolder;
    }

    private GoConfigHolder reloadConfig(String configXml, final List<PartialConfig> partials) throws Exception {
        try {
            return loader.loadConfigHolder(configXml, new MagicalGoConfigXmlLoader.Callback() {
                @Override
                public void call(CruiseConfig cruiseConfig) {
                    cruiseConfig.setPartials(partials);
                }
            });
        } catch (Exception e) {
            LOGGER.info(format("[CONFIG_MERGE] Post merge validation failed"));
            throw new ConfigMergePostValidationException(e.getMessage(), e);
        }
    }

    private String getMergedConfig(String modifiedConfigAsXml, String currentUser, String oldMd5) throws Exception {
        GoConfigRevision configRevision = new GoConfigRevision(modifiedConfigAsXml, "temporary-md5-for-branch", currentUser,
                serverVersion.version(), timeProvider);

        return configRepository.getConfigMergedWithLatestRevision(configRevision, oldMd5);
    }

    private String toXmlString(CruiseConfig configForEdit, String md5) {
        LOGGER.info(format("[CONFIG_MERGE] Validating and serializing CruiseConfig to xml before merge: Starting"));
        String configForEditXml;

        try {
            preprocessAndValidate(configForEdit);
            configForEditXml = toXmlString(configForEdit);
        } catch (Exception e) {
            LOGGER.info(format("[CONFIG_MERGE] Pre merge validation failed, latest-md5: %s", md5));
            throw new ConfigMergePreValidationException(e.getMessage(), e);
        }
        LOGGER.info(format("[CONFIG_MERGE] Validating and serializing CruiseConfig to xml before merge: Done"));

        return configForEditXml;
    }
}