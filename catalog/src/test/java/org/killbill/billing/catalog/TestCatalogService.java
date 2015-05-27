/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014 Groupon, Inc
 * Copyright 2014 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.killbill.billing.catalog;

import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.io.VersionedCatalogLoader;
import org.killbill.billing.platform.api.KillbillService.ServiceException;
import org.killbill.billing.util.config.CatalogConfig;
import org.killbill.clock.DefaultClock;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestCatalogService extends CatalogTestSuiteNoDB {

    @Test(groups = "fast")
    public void testCatalogServiceDirectory() throws ServiceException, CatalogApiException {
        final DefaultCatalogService service = new DefaultCatalogService(new CatalogConfig() {
            @Override
            public String getCatalogURI() {
                return "file:src/test/resources/versionedCatalog";
            }

        }, tenantInternalApi, catalogCache, cacheInvalidationCallback);
        service.loadCatalog();
        Assert.assertNotNull(service.getFullCatalog(internalCallContext));
        Assert.assertEquals(service.getFullCatalog(internalCallContext).getCatalogName(), "WeaponsHireSmall");
    }

    @Test(groups = "fast")
    public void testCatalogServiceFile() throws ServiceException, CatalogApiException {
        final DefaultCatalogService service = new DefaultCatalogService(new CatalogConfig() {
            @Override
            public String getCatalogURI() {
                return "file:src/test/resources/WeaponsHire.xml";
            }

        },  tenantInternalApi, catalogCache, cacheInvalidationCallback);
        service.loadCatalog();
        Assert.assertNotNull(service.getFullCatalog(internalCallContext));
        Assert.assertEquals(service.getFullCatalog(internalCallContext).getCatalogName(), "Firearms");
    }
}
