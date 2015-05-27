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

package org.killbill.billing.catalog.rules;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlIDREF;

import org.testng.Assert;
import org.testng.annotations.Test;

import org.killbill.billing.ErrorCode;
import org.killbill.billing.catalog.CatalogTestSuiteNoDB;
import org.killbill.billing.catalog.DefaultPriceList;
import org.killbill.billing.catalog.DefaultProduct;
import org.killbill.billing.catalog.MockCatalog;
import org.killbill.billing.catalog.StandaloneCatalog;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.CatalogApiException;
import org.killbill.billing.catalog.api.PlanSpecifier;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.catalog.api.ProductCategory;

public class TestCase extends CatalogTestSuiteNoDB {

    protected class CaseResult extends Case<Result> {

        @XmlElement(required = true)
        private final Result policy;

        public CaseResult(final DefaultProduct product, final ProductCategory productCategory, final BillingPeriod billingPeriod, final DefaultPriceList priceList,
                          final Result policy) {
            setProduct(product);
            setProductCategory(productCategory);
            setBillingPeriod(billingPeriod);
            setPriceList(priceList);
            this.policy = policy;
        }

        @Override
        protected Result getResult() {
            return policy;
        }

        @XmlElement(required = false, name = "product")
        @XmlIDREF
        protected DefaultProduct product;
        @XmlElement(required = false, name = "productCategory")
        protected ProductCategory productCategory;

        @XmlElement(required = false, name = "billingPeriod")
        protected BillingPeriod billingPeriod;

        @XmlElement(required = false, name = "priceList")
        @XmlIDREF
        protected DefaultPriceList priceList;

        public DefaultProduct getProduct() {
            return product;
        }

        public ProductCategory getProductCategory() {
            return productCategory;
        }

        public BillingPeriod getBillingPeriod() {
            return billingPeriod;
        }

        public DefaultPriceList getPriceList() {
            return priceList;
        }

        protected CaseResult setProduct(final DefaultProduct product) {
            this.product = product;
            return this;
        }

        protected CaseResult setProductCategory(final ProductCategory productCategory) {
            this.productCategory = productCategory;
            return this;
        }

        protected CaseResult setBillingPeriod(final BillingPeriod billingPeriod) {
            this.billingPeriod = billingPeriod;
            return this;
        }

        protected CaseResult setPriceList(final DefaultPriceList priceList) {
            this.priceList = priceList;
            return this;
        }
    }

    @Test(groups = "fast")
    public void testBasic() throws CatalogApiException {
        final MockCatalog cat = new MockCatalog();

        final DefaultProduct product = cat.getCurrentProducts()[0];
        final DefaultPriceList priceList = cat.findCurrentPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

        final CaseResult cr = new CaseResult(
                product,
                ProductCategory.BASE,
                BillingPeriod.MONTHLY,
                priceList,
                Result.FOO);

        assertion(Result.FOO, cr, product.getName(), ProductCategory.BASE, BillingPeriod.MONTHLY, priceList.getName(), cat);
        assertionNull(cr, cat.getCurrentProducts()[1].getName(), ProductCategory.BASE, BillingPeriod.MONTHLY, priceList.getName(), cat);
        assertionNull(cr, product.getName(), ProductCategory.ADD_ON, BillingPeriod.MONTHLY, priceList.getName(), cat);
        assertionNull(cr, product.getName(), ProductCategory.BASE, BillingPeriod.ANNUAL, priceList.getName(), cat);
        assertionException(cr, product.getName(), ProductCategory.BASE, BillingPeriod.MONTHLY, "dipsy", cat);
    }

    @Test(groups = "fast")
    public void testWildCardProduct() throws CatalogApiException {
        final MockCatalog cat = new MockCatalog();

        final DefaultProduct product = cat.getCurrentProducts()[0];
        final DefaultPriceList priceList = cat.findCurrentPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

        final CaseResult cr = new CaseResult(
                null,
                ProductCategory.BASE,
                BillingPeriod.MONTHLY,
                priceList,

                Result.FOO);

        assertion(Result.FOO, cr, product.getName(), ProductCategory.BASE, BillingPeriod.MONTHLY, priceList.getName(), cat);
        assertion(Result.FOO, cr, cat.getCurrentProducts()[1].getName(), ProductCategory.BASE, BillingPeriod.MONTHLY, priceList.getName(), cat);
        assertionNull(cr, product.getName(), ProductCategory.ADD_ON, BillingPeriod.MONTHLY, priceList.getName(), cat);
        assertionNull(cr, product.getName(), ProductCategory.BASE, BillingPeriod.ANNUAL, priceList.getName(), cat);
        assertionException(cr, product.getName(), ProductCategory.BASE, BillingPeriod.MONTHLY, "dipsy", cat);
    }

    @Test(groups = "fast")
    public void testWildCardProductCategory() throws CatalogApiException {
        final MockCatalog cat = new MockCatalog();

        final DefaultProduct product = cat.getCurrentProducts()[0];
        final DefaultPriceList priceList = cat.findCurrentPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

        final CaseResult cr = new CaseResult(
                product,
                null,
                BillingPeriod.MONTHLY,
                priceList,

                Result.FOO);

        assertion(Result.FOO, cr, product.getName(), ProductCategory.BASE, BillingPeriod.MONTHLY, priceList.getName(), cat);
        assertionNull(cr, cat.getCurrentProducts()[1].getName(), ProductCategory.BASE, BillingPeriod.MONTHLY, priceList.getName(), cat);
        assertion(Result.FOO, cr, product.getName(), ProductCategory.ADD_ON, BillingPeriod.MONTHLY, priceList.getName(), cat);
        assertionNull(cr, product.getName(), ProductCategory.BASE, BillingPeriod.ANNUAL, priceList.getName(), cat);
        assertionException(cr, product.getName(), ProductCategory.BASE, BillingPeriod.MONTHLY, "dipsy", cat);
    }

    @Test(groups = "fast")
    public void testWildCardBillingPeriod() throws CatalogApiException {
        final MockCatalog cat = new MockCatalog();

        final DefaultProduct product = cat.getCurrentProducts()[0];
        final DefaultPriceList priceList = cat.findCurrentPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

        final CaseResult cr = new CaseResult(
                product,
                ProductCategory.BASE,
                null,
                priceList,

                Result.FOO);

        assertion(Result.FOO, cr, product.getName(), ProductCategory.BASE, BillingPeriod.MONTHLY, priceList.getName(), cat);
        assertionNull(cr, cat.getCurrentProducts()[1].getName(), ProductCategory.BASE, BillingPeriod.MONTHLY, priceList.getName(), cat);
        assertionNull(cr, product.getName(), ProductCategory.ADD_ON, BillingPeriod.MONTHLY, priceList.getName(), cat);
        assertion(Result.FOO, cr, product.getName(), ProductCategory.BASE, BillingPeriod.ANNUAL, priceList.getName(), cat);
        assertionException(cr, product.getName(), ProductCategory.BASE, BillingPeriod.MONTHLY, "dipsy", cat);
    }

    @Test(groups = "fast")
    public void testWildCardPriceList() throws CatalogApiException {
        final MockCatalog cat = new MockCatalog();

        final DefaultProduct product = cat.getCurrentProducts()[0];
        final DefaultPriceList priceList = cat.findCurrentPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

        final CaseResult cr = new CaseResult(
                product,
                ProductCategory.BASE,
                BillingPeriod.MONTHLY,
                null,

                Result.FOO);

        assertion(Result.FOO, cr, product.getName(), ProductCategory.BASE, BillingPeriod.MONTHLY, priceList.getName(), cat);
        assertionNull(cr, cat.getCurrentProducts()[1].getName(), ProductCategory.BASE, BillingPeriod.MONTHLY, priceList.getName(), cat);
        assertionNull(cr, product.getName(), ProductCategory.ADD_ON, BillingPeriod.MONTHLY, priceList.getName(), cat);
        assertionNull(cr, product.getName(), ProductCategory.BASE, BillingPeriod.ANNUAL, priceList.getName(), cat);
        assertion(Result.FOO, cr, product.getName(), ProductCategory.BASE, BillingPeriod.MONTHLY, "dipsy", cat);
    }

    @Test
    public void testCaseOrder() throws CatalogApiException {
        final MockCatalog cat = new MockCatalog();

        final DefaultProduct product = cat.getCurrentProducts()[0];
        final DefaultPriceList priceList = cat.findCurrentPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);

        final CaseResult cr0 = new CaseResult(
                product,
                ProductCategory.BASE,
                BillingPeriod.MONTHLY,
                priceList,
                Result.FOO);

        final CaseResult cr1 = new CaseResult(
                product,
                ProductCategory.BASE,
                BillingPeriod.MONTHLY,
                priceList,
                Result.BAR);

        final CaseResult cr2 = new CaseResult(
                product,
                ProductCategory.BASE,
                BillingPeriod.ANNUAL,
                priceList,
                Result.DIPSY);

        final CaseResult cr3 = new CaseResult(
                product,
                ProductCategory.BASE,
                BillingPeriod.ANNUAL,
                priceList,
                Result.LALA);

        final Result r1 = Case.getResult(new CaseResult[]{cr0, cr1, cr2, cr3},
                                         new PlanSpecifier(product.getName(), product.getCategory(), BillingPeriod.MONTHLY, priceList.getName()), cat);
        Assert.assertEquals(r1, Result.FOO);

        final Result r2 = Case.getResult(new CaseResult[]{cr0, cr1, cr2},
                                         new PlanSpecifier(product.getName(), product.getCategory(), BillingPeriod.ANNUAL, priceList.getName()), cat);
        Assert.assertEquals(r2, Result.DIPSY);
    }

    protected void assertionNull(final CaseResult cr, final String productName, final ProductCategory productCategory, final BillingPeriod bp, final String priceListName, final StandaloneCatalog cat) throws CatalogApiException {
        Assert.assertNull(cr.getResult(new PlanSpecifier(productName, productCategory, bp, priceListName), cat));
    }

    protected void assertionException(final CaseResult cr, final String productName, final ProductCategory productCategory, final BillingPeriod bp, final String priceListName, final StandaloneCatalog cat) {
        try {
            cr.getResult(new PlanSpecifier(productName, productCategory, bp, priceListName), cat);
            Assert.fail("Expecting an exception");
        } catch (CatalogApiException e) {
            Assert.assertEquals(e.getCode(), ErrorCode.CAT_PRICE_LIST_NOT_FOUND.getCode());
        }
    }

    protected void assertion(final Result result, final CaseResult cr, final String productName, final ProductCategory productCategory, final BillingPeriod bp, final String priceListName, final StandaloneCatalog cat) throws CatalogApiException {
        Assert.assertEquals(result, cr.getResult(new PlanSpecifier(productName, productCategory, bp, priceListName), cat));
    }
}
