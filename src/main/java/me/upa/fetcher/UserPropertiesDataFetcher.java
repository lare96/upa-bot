package me.upa.fetcher;

import com.google.common.base.Objects;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.upa.fetcher.UserPropertiesDataFetcher.UserProperty;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class UserPropertiesDataFetcher extends ApiDataFetcher<Map<Long, UserProperty>> {

    @Override
    public Map<Long, UserProperty> getResult() {
        return properties;
    }

    @Override
    public Map<Long, UserProperty> getDefaultResult() {
        return properties;
    }

    public static final class UserProperty {
        private final long propId;
        private final int cityId;
        private final int neighborhoodId;
        private final String fullAddress;
        private final String status;
        private final int upxSalePrice;
        private final int fiatSalePrice;
        private final String inGameName;

        public UserProperty(long propId, int cityId, int neighborhoodId, String fullAddress, String status, int upxSalePrice, int fiatSalePrice, String inGameName) {
            this.propId = propId;
            this.cityId = cityId;
            this.neighborhoodId = neighborhoodId;
            this.fullAddress = fullAddress;
            this.status = status;
            this.upxSalePrice = upxSalePrice;
            this.fiatSalePrice = fiatSalePrice;
            this.inGameName = inGameName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            UserProperty property = (UserProperty) o;
            return Objects.equal(propId, property.propId);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(propId);
        }

        public long getPropId() {
            return propId;
        }

        public int getCityId() {
            return cityId;
        }

        public int getNeighborhoodId() {
            return neighborhoodId;
        }

        public String getFullAddress() {
            return fullAddress;
        }

        public String getStatus() {
            return status;
        }

        public int getUpxSalePrice() {
            return upxSalePrice;
        }

        public int getFiatSalePrice() {
            return fiatSalePrice;
        }

        public String getOwnerUsername() {
            return inGameName;
        }
    }
    private static final Logger logger = LogManager.getLogger();

    private final Map<Long, UserProperty> properties = new ConcurrentHashMap<>();
    private final String user;

    public UserPropertiesDataFetcher(String user) {
        this.user = user;
    }

    @Override
    protected void handleResponse(String link, String response) {
        if (response.contains("Internal Server Error")) {
            throw new IllegalStateException("Internal server error.");
        }
        try {
            JsonElement jsonProperties = ApiDataFetcher.GSON.fromJson(response, JsonElement.class);
            if(jsonProperties.isJsonNull())
                return;
            if (jsonProperties.isJsonObject()) {
                JsonObject dataObject = jsonProperties.getAsJsonObject();
                if (dataObject.has("message") && dataObject.get("message").getAsString().equals("Cannot read properties of null (reading 'eos_id')")) {
                    return;
                }
            }
            for (JsonElement next : jsonProperties.getAsJsonArray()) {
                JsonObject data = next.getAsJsonObject();
                long propId = data.get("prop_id").getAsLong();
                int cityId = DataFetcherManager.getCityId(data.get("city_name").getAsString());
                JsonElement neighborhoodElement = data.get("neighborhood");
                int neighborhoodId = neighborhoodElement.isJsonNull() ? -1 : DataFetcherManager.getNeighborhoodId(neighborhoodElement.getAsString());
                String fullAddress = data.get("full_address").getAsString();
                String status = data.get("status").getAsString();
                JsonElement upxSalePriceElement = data.get("sale_upx_price");
                JsonElement fiatSalePriceElement = data.get("sale_fiat_price");
                int upxSalePrice = upxSalePriceElement.isJsonNull() ? -1 : upxSalePriceElement.getAsInt();
                int fiatSalePrice = fiatSalePriceElement.isJsonNull() ? -1 : fiatSalePriceElement.getAsInt();
                properties.put(propId, new UserProperty(propId, cityId, neighborhoodId, fullAddress, status, upxSalePrice, fiatSalePrice, user));
            }
        } catch (Exception e) {
            logger.catching(e);
        }
    }

    @Override
    protected List<String> computeFetchLinks() {
        return List.of("https://api.upland.me/properties/list/" + user);
    }
}
