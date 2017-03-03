package alien4cloud.rest.deployment.model;

import java.util.Map;

import alien4cloud.Constants;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

/**
 * Request to query for timed data.
 */
@Getter
@Setter
public class TimedRequest {
    /** index from which to get results. */
    private int from;
    /** number of results to get. */
    private int size;
    /** The beginning of the interval for which to get data. */
    @NonNull
    private Long intervalStart;
    /** Optional end interval. */
    private Long intervalEnd;

    /**
     * Set the value for 'from': start element in the request.
     *
     * @param from Initial element index for the request. If null the value will be set to 0.
     */
    public void setFrom(Integer from) {
        if (from == null) {
            this.from = 0;
        } else {
            this.from = from;
        }
    }

    /**
     * Set the value for the size (maximum number of elements to return in the request).
     *
     * @param size Maximum number of elements to return in the request. If null will be set to 50. Cannot be more than 100.
     */
    public void setSize(Integer size) {
        if (size == null) {
            this.size = Constants.DEFAULT_ES_SEARCH_SIZE;
        } else if (size > Constants.MAX_ES_SEARCH_SIZE) {
            this.size = Constants.MAX_ES_SEARCH_SIZE;
        } else {
            this.size = size;
        }
    }
}