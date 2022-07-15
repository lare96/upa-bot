package me.upa.fetcher;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import me.upa.game.Sale;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;

public final class CsvSalesDataFetcher extends CsvDataFetcher {

    private static final class FetchedSale {
        private final String address;
        private final String street;
        private final String city;
        private final String neighborhood;
        private final String collection;
        private final String boost;
        private final String doubleCollection;
        private final String doubleBoost;
        private final String building;
        private final double mintUpx;
        private final double monthlyUpx;
        private final double up2;
        private final String currency;
        private final double price;
        private final double priceAsUpx;
        private final double tax;
        private final double markup;
        private final String owner;

        public FetchedSale() {
            address = null;
            street = null;
            city = null;
            neighborhood = null;
            collection = null;
            boost = null;
            doubleCollection = null;
            doubleBoost = null;
            building = null;
            mintUpx = 0.0;
            monthlyUpx = 0.0;
            up2 = 0.0;
            currency = null;
            price = 0.0;
            priceAsUpx = 0.0;
            tax = 0.0;
            markup = 0.0;
            owner = null;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("address", address)
                    .add("street", street)
                    .add("city", city)
                    .add("neighborhood", neighborhood)
                    .add("collection", collection)
                    .add("boost", boost)
                    .add("doubleCollection", doubleCollection)
                    .add("doubleBoost", doubleBoost)
                    .add("building", building)
                    .add("mintUpx", mintUpx)
                    .add("monthlyUpx", monthlyUpx)
                    .add("up2", up2)
                    .add("currency", currency)
                    .add("price", price)
                    .add("priceAsUpx", priceAsUpx)
                    .add("tax", tax)
                    .add("markup", markup)
                    .add("owner", owner)
                    .toString();
        }

        public String getAddress() {
            return address;
        }


        public String getStreet() {
            return street;
        }

        public String getCity() {
            return city;
        }

        public String getNeighborhood() {
            return neighborhood;
        }

        public String getCollection() {
            return collection;
        }

        public String getDoubleCollection() {
            return doubleCollection;
        }

        public String getBoost() {
            return boost;
        }

        public String getDoubleBoost() {
            return doubleBoost;
        }

        public String getBuilding() {
            return building;
        }

        public double getMintUpx() {
            return mintUpx;
        }

        public double getMonthlyUpx() {
            return monthlyUpx;
        }

        public double getUp2() {
            return up2;
        }

        public String getCurrency() {
            return currency;
        }

        public double getPrice() {
            return price;
        }

        public double getPriceAsUpx() {
            return priceAsUpx;
        }

        public double getTax() {
            return tax;
        }

        public double getMarkup() {
            return markup;
        }

        public String getOwner() {
            return owner;
        }

    }

    @JsonPropertyOrder(value = {"Address", "Street", "City", "Neighborhood", "Collection", "Boost",
            "Double Collection", "Double Boost", "Building", "Mint UPX", "Monthly UPX", "UP2", "Currency", "Price",
            "Price as UPX", "Tax", "Markup", "Owner"})
    private static abstract class FetchedSaleFormat {
        @JsonProperty("Address")
        public abstract String getAddress();

        @JsonProperty("Street")
        public abstract String getStreet();

        @JsonProperty("City")
        public abstract String getCity();

        @JsonProperty("Neighborhood")
        public abstract String getNeighborhood();

        @JsonProperty("Collection")
        public abstract String getCollection();

        @JsonProperty("Double Collection")
        public abstract String getDoubleCollection();

        @JsonProperty("Boost")
        public abstract String getBoost();

        @JsonProperty("Double Boost")
        public abstract String getDoubleBoost();

        @JsonProperty("Building")
        public abstract String getBuilding();

        @JsonProperty("Mint UPX")
        public abstract double getMintUpx();

        @JsonProperty("Monthly UPX")
        public abstract double getMonthlyUpx();

        @JsonProperty("UP2")
        public abstract double getUp2();

        @JsonProperty("Currency")
        public abstract String getCurrency();

        @JsonProperty("Price")
        public abstract double getPrice();

        @JsonProperty("Price as UPX")
        public abstract double getPriceAsUpx();

        @JsonProperty("Tax")
        public abstract double getTax();

        @JsonProperty("Markup")
        public abstract double getMarkup();

        @JsonProperty("Owner")
        public abstract String getOwner();
    }
    private static final Logger logger = LogManager.getLogger();

    @Override
    protected void handleResponse(String link, String response) {
        CsvMapper csvMapper = new CsvMapper();
        csvMapper.enable(CsvParser.Feature.IGNORE_TRAILING_UNMAPPABLE);
        csvMapper.addHandler(new DeserializationProblemHandler() {
            @Override
            public Object handleWeirdStringValue(DeserializationContext ctxt, Class<?> targetType, String valueToConvert, String failureMsg) throws IOException {
                return null;
            }

            @Override
            public Object handleWeirdNumberValue(DeserializationContext ctxt, Class<?> targetType, Number valueToConvert, String failureMsg) throws IOException {
                return 0.0;
            }
        });

        try {
            csvMapper.addMixIn(FetchedSale.class, FetchedSaleFormat.class);
            CsvSchema schema = csvMapper.schemaFor(FetchedSale.class).withHeader();
            MappingIterator<FetchedSale> mappingIterator = csvMapper.readerFor(FetchedSale.class).with(schema).readValues(response);
            while (mappingIterator.hasNext()) {
                FetchedSale fetchedSale = mappingIterator.next();
                String cityNameValue = fetchedSale.getCity();
                double up2Value = fetchedSale.getUp2();
                double mintUpxValue = fetchedSale.getMintUpx();
                String currencyValue = fetchedSale.getCurrency();
                String addressValue = fetchedSale.getAddress();
                String neighborhoodValue = fetchedSale.getNeighborhood();
                double priceAsUpxValue = fetchedSale.getPriceAsUpx();
                double markupValue = fetchedSale.getMarkup();
                String ownerValue = fetchedSale.getOwner();
                if (up2Value == 0.0 || Strings.isNullOrEmpty(cityNameValue) || mintUpxValue == 0.0 || !Objects.equals("UPX", currencyValue)
                        || addressValue == null || neighborhoodValue == null || priceAsUpxValue == 0.0 || markupValue == 0.0
                        || Strings.isNullOrEmpty(ownerValue))
                    continue;
                int cityId = DataFetcherManager.getCityId(cityNameValue);
                if (cityId == -1)
                    continue;
                int neighborhoodId = DataFetcherManager.getNeighborhoodId(neighborhoodValue);
                Sale sale = new Sale(cityId, neighborhoodId, "-1", (int) priceAsUpxValue, (int) mintUpxValue, (int) up2Value,
                        fetchedSale.getCollection(), (int) markupValue, addressValue, ownerValue);
                DataFetcherManager.addSale(cityId, sale);
            }
        } catch (IOException e) {
           logger.error("Error fetching sales data.", e);
        }
    }

    @Override
    protected List<String> computeFetchLinks() {
        String baseLink = "https://upx-spark.exchange/cities/download/";
        List<String> links = new ArrayList<>();
        var sb = new StringBuilder();
        for (var entry : DataFetcherManager.getCityMap().entrySet()) {
            links.add(sb.append(baseLink).append(entry.getKey()).toString());
            sb.setLength(0);
        }
        return links;
    }
}
