/*
 * Copyright 2010-2013 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
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

package org.killbill.billing.catalog.api.user;

import javax.inject.Inject;

import org.killbill.billing.callcontext.InternalCallContext;
import org.killbill.billing.callcontext.InternalTenantContext;
import org.killbill.billing.catalog.api.Catalog;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.CatalogService;
import org.killbill.billing.catalog.api.CatalogUserApi;
import org.killbill.billing.catalog.api.StaticCatalog;
import org.killbill.billing.catalog.caching.CatalogCache;
import org.killbill.billing.tenant.api.TenantApiException;
import org.killbill.billing.tenant.api.TenantKV.TenantKey;
import org.killbill.billing.tenant.api.TenantUserApi;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.callcontext.InternalCallContextFactory;
import org.killbill.billing.util.callcontext.TenantContext;

public class DefaultCatalogUserApi implements CatalogUserApi {

    private final CatalogService catalogService;
    private final InternalCallContextFactory internalCallContextFactory;
    private final TenantUserApi tenantApi;
    private final CatalogCache catalogCache;

    @Inject
    public DefaultCatalogUserApi(final CatalogService catalogService,
                                 final TenantUserApi tenantApi,
                                 final CatalogCache catalogCache,
                                 final InternalCallContextFactory internalCallContextFactory) {
        this.catalogService = catalogService;
        this.tenantApi = tenantApi;
        this.catalogCache = catalogCache;
        this.internalCallContextFactory = internalCallContextFactory;
    }

    @Override
    public Catalog getCatalog(final String catalogName, final TenantContext tenantContext) throws CatalogApiException {
        final InternalTenantContext internalTenantContext = internalCallContextFactory.createInternalTenantContext(tenantContext);
        return catalogService.getFullCatalog(internalTenantContext);
    }

    @Override
    public StaticCatalog getCurrentCatalog(final String catalogName, final TenantContext tenantContext) throws CatalogApiException {
        final InternalTenantContext internalTenantContext = createInternalTenantContext(tenantContext);
        return catalogService.getCurrentCatalog(internalTenantContext);
    }

    @Override
    public void uploadCatalog(final String catalogXML, final CallContext callContext) throws CatalogApiException {
        try {
            final InternalTenantContext internalTenantContext = createInternalTenantContext(callContext);
            catalogCache.clearCatalog(internalTenantContext);
            tenantApi.addTenantKeyValue(TenantKey.CATALOG.toString(), catalogXML, callContext);
        } catch (TenantApiException e) {
            throw new CatalogApiException(e);
        }
    }

    private InternalTenantContext createInternalTenantContext(final TenantContext tenantContext) {
        // Only tenantRecordId will be populated-- this is important to always create the (ehcache) key the same way
        return internalCallContextFactory.createInternalTenantContext(tenantContext);
    }

}
