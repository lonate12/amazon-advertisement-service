package com.amazon.ata.advertising.service.businesslogic;

import com.amazon.ata.advertising.service.dao.CustomerProfileDao;
import com.amazon.ata.advertising.service.dao.ReadableDao;
import com.amazon.ata.advertising.service.model.*;
import com.amazon.ata.advertising.service.targeting.TargetingGroup;
import com.amazon.ata.advertising.service.targeting.TargetingEvaluator;

import com.amazon.ata.advertising.service.targeting.predicate.TargetingPredicateResult;
import com.amazon.ata.customerservice.CustomerProfile;
import com.amazonaws.services.dynamodbv2.xspec.L;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.Predicate;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.Opt;

import java.util.*;
import java.util.stream.Collectors;
import javax.inject.Inject;

/**
 * This class is responsible for picking the advertisement to be rendered.
 */
public class AdvertisementSelectionLogic {

    private static final Logger LOG = LogManager.getLogger(AdvertisementSelectionLogic.class);

    private final ReadableDao<String, List<AdvertisementContent>> contentDao;
    private final ReadableDao<String, List<TargetingGroup>> targetingGroupDao;
    private final ReadableDao<String, CustomerProfile> customerProfileDao;
    private Random random = new Random();

    /**
     * Constructor for AdvertisementSelectionLogic.
     * @param contentDao Source of advertising content.
     * @param targetingGroupDao Source of targeting groups for each advertising content.
     */
    @Inject
    public AdvertisementSelectionLogic(ReadableDao<String, List<AdvertisementContent>> contentDao,
                                       ReadableDao<String, List<TargetingGroup>> targetingGroupDao,
                                       ReadableDao<String, CustomerProfile> customerProfileDao) {
        this.contentDao = contentDao;
        this.targetingGroupDao = targetingGroupDao;
        this.customerProfileDao = customerProfileDao;
    }

    /**
     * Setter for Random class.
     * @param random generates random number used to select advertisements.
     */
    public void setRandom(Random random) {
        this.random = random;
    }

    /**
     * Gets all of the content and metadata for the marketplace and determines which content can be shown.  Returns the
     * eligible content with the highest click through rate.  If no advertisement is available or eligible, returns an
     * EmptyGeneratedAdvertisement.
     *
     * @param customerId - the customer to generate a custom advertisement for
     * @param marketplaceId - the id of the marketplace the advertisement will be rendered on
     * @return an advertisement customized for the customer id provided, or an empty advertisement if one could
     *     not be generated.
     */
    public GeneratedAdvertisement selectAdvertisement(String customerId, String marketplaceId) {
        GeneratedAdvertisement generatedAdvertisement = new EmptyGeneratedAdvertisement();

        if (StringUtils.isEmpty(marketplaceId)) {
            LOG.warn("MarketplaceId cannot be null or empty. Returning empty ad.");
            return generatedAdvertisement;
        }

        final List<AdvertisementContent> contents = contentDao.get(marketplaceId);

        if (CollectionUtils.isNotEmpty(contents)) {
            TargetingEvaluator targetingEvaluator = new TargetingEvaluator(new RequestContext(customerId, marketplaceId));
            // Note: Initial, hacky solution replaced by a cleaner solution
            //  The cleaner solution was helped along by Swastik
            // TreeMap<Double, List<AdvertisementContent>> contentTreeMap = new TreeMap<>();

//            contents.stream()
//                    .forEach(content -> {
//                        targetingGroupDao.get(content.getContentId()).stream()
//                                .forEach(targetingGroup -> {
//                                    if (targetingEvaluator.evaluate(targetingGroup).isTrue()){
//                                        System.out.println("Targeting Group " + targetingGroup.getTargetingGroupId() + " evaluates to true.");
//                                        // Get the list at the current click through rate
//                                        List<AdvertisementContent> listAtCurrentClickThrough = contentTreeMap.get(targetingGroup.getClickThroughRate());
//                                        // If the list is empty, create a new list
//                                        if (listAtCurrentClickThrough == null) {
//                                            listAtCurrentClickThrough = new ArrayList<>();
//                                        }
//                                        // Add the content to the new list
//                                        listAtCurrentClickThrough.add(content);
//                                        // Add the updated list to the treemap at the appropriate click through rate
//                                        contentTreeMap.put(targetingGroup.getClickThroughRate(),listAtCurrentClickThrough);
//                                    } else {
//                                        System.out.println("Targeting Group " + targetingGroup.getTargetingGroupId() + " does not evaluate to true.");
//                                    }
//                                });
//                    });

            TreeMap<TargetingGroup, AdvertisementContent> contentTreeMap = new TreeMap<>(Comparator.comparingDouble(TargetingGroup::getClickThroughRate));
            for (AdvertisementContent content : contents) {
                for (TargetingGroup tg : targetingGroupDao.get(content.getContentId())) {
                    if (targetingEvaluator.evaluate(tg).isTrue()) {
                        contentTreeMap.put(tg, content);
                    }
                }
            }

            if (!contentTreeMap.isEmpty()) {
                generatedAdvertisement = new GeneratedAdvertisement(contentTreeMap.lastEntry().getValue());
            }
        }

        return generatedAdvertisement;
    }
}
