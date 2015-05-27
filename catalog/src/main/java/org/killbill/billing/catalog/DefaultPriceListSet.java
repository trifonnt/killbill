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

package org.killbill.billing.catalog;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import java.util.ArrayList;
import java.util.List;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.PriceList;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.catalog.api.Product;
import org.killbill.xmlloader.ValidatingConfig;
import org.killbill.xmlloader.ValidationError;
import org.killbill.xmlloader.ValidationErrors;

@XmlAccessorType(XmlAccessType.NONE)
public class DefaultPriceListSet extends ValidatingConfig<StandaloneCatalog> {
    @XmlElement(required = true, name = "defaultPriceList")
    private PriceListDefault defaultPricelist;

    @XmlElement(required = false, name = "childPriceList")
    private DefaultPriceList[] childPriceLists = new DefaultPriceList[0];

    public DefaultPriceListSet() {
        if (childPriceLists == null) {
            childPriceLists = new DefaultPriceList[0];
        }
    }

    public DefaultPriceListSet(final PriceListDefault defaultPricelist, final DefaultPriceList[] childPriceLists) {
        this.defaultPricelist = defaultPricelist;
        this.childPriceLists = childPriceLists;
    }

    public DefaultPlan getPlanFrom(final String priceListName, final Product product,
                                   final BillingPeriod period) throws CatalogApiException {
        DefaultPlan result = null;
        final DefaultPriceList pl = findPriceListFrom(priceListName);
        if (pl != null) {
            result = pl.findPlan(product, period);
        }
        if (result != null) {
            return result;
        }

        return defaultPricelist.findPlan(product, period);
    }

    public DefaultPriceList findPriceListFrom(final String priceListName) throws CatalogApiException {
        if (priceListName == null) {
            throw new CatalogApiException(ErrorCode.CAT_NULL_PRICE_LIST_NAME);
        }
        if (defaultPricelist.getName().equals(priceListName)) {
            return defaultPricelist;
        }
        for (final DefaultPriceList pl : childPriceLists) {
            if (pl.getName().equals(priceListName)) {
                return pl;
            }
        }
        throw new CatalogApiException(ErrorCode.CAT_PRICE_LIST_NOT_FOUND, priceListName);
    }

    @Override
    public ValidationErrors validate(final StandaloneCatalog catalog, final ValidationErrors errors) {
        defaultPricelist.validate(catalog, errors);
        //Check that the default pricelist name is not in use in the children
        for (final DefaultPriceList pl : childPriceLists) {
            if (pl.getName().equals(PriceListSet.DEFAULT_PRICELIST_NAME)) {
                errors.add(new ValidationError("Pricelists cannot use the reserved name '" + PriceListSet.DEFAULT_PRICELIST_NAME + "'",
                                               catalog.getCatalogURI(), DefaultPriceListSet.class, pl.getName()));
            }
            pl.validate(catalog, errors); // and validate the individual pricelists
        }
        return errors;
    }

    public DefaultPriceList getDefaultPricelist() {
        return defaultPricelist;
    }

    public DefaultPriceList[] getChildPriceLists() {
        return childPriceLists;
    }

    public List<PriceList> getAllPriceLists() {
        final List<PriceList> result = new ArrayList<PriceList>(childPriceLists.length + 1);
        result.add(getDefaultPricelist());
        for (final PriceList list : getChildPriceLists()) {
            result.add(list);
        }
        return result;
    }


}
