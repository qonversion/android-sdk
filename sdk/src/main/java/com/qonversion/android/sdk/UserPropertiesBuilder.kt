package com.qonversion.android.sdk

import com.qonversion.android.sdk.dto.UserProperty

/**
 * This builder class can be used to generate a map of user properties
 * which can be then provided to [Qonversion.setUserProperties].
 * It consumes both Qonversion defined and custom properties.
 */
class UserPropertiesBuilder {

    internal val properties = mutableMapOf<String, String>()

    /**
     * Set current user name.
     * @param name name to set to the current user
     * @return builder instance for chain calls
     */
    fun setName(name: String): UserPropertiesBuilder = apply {
        properties[UserProperty.Name.code] = name
    }

    /**
     * Set custom user id. It can be an identifier used on your backend
     * to tie the current Qonversion user with your one.
     * @param customUserId user id
     * @return builder instance for chain calls
     */
    fun setCustomUserId(customUserId: String): UserPropertiesBuilder = apply {
        properties[UserProperty.CustomUserId.code] = customUserId
    }

    /**
     * Set current user email address.
     * @param email email address to set to the current user
     * @return builder instance for chain calls
     */
    fun setEmail(email: String): UserPropertiesBuilder = apply {
        properties[UserProperty.Email.code] = email
    }

    /**
     * Set Kochava unique device id.
     * @param deviceId Kochava unique device id
     * @return builder instance for chain calls
     */
    fun setKochavaDeviceId(deviceId: String): UserPropertiesBuilder = apply {
        properties[UserProperty.KochavaDeviceId.code] = deviceId
    }

    /**
     * Set AppsFlyer user id. Can be used to cross-reference your in-house data
     * with AppsFlyer attribution data.
     * @param userId Appsflyer user id
     * @return builder instance for chain calls
     */
    fun setAppsFlyerUserId(userId: String): UserPropertiesBuilder = apply {
        properties[UserProperty.AppsFlyerUserId.code] = userId
    }

    /**
     * Set Adjust advertising id.
     * @param advertisingId Adjust advertising id
     * @return builder instance for chain calls
     */
    fun setAdjustAdvertisingId(advertisingId: String): UserPropertiesBuilder = apply {
        properties[UserProperty.AdjustAdId.code] = advertisingId
    }

    /**
     * Set Facebook attribution - mobile Cookie from the user's device.
     * @param facebookAttribution Facebook attribution string
     * @return builder instance for chain calls
     */
    fun setFacebookAttribution(facebookAttribution: String): UserPropertiesBuilder = apply {
        properties[UserProperty.FacebookAttribution.code] = facebookAttribution
    }

    /**
     * Set user property with custom key different from defined ones.
     * @param key nonempty key for user property consisting of letters A-Za-z, numbers, and symbols _.:-
     * @param value nonempty value for the given property
     */
    fun setCustomUserProperty(key: String, value: String): UserPropertiesBuilder = apply {
        properties[key] = value
    }

    /**
     * Build final properties map with all the properties provided to builder before.
     * @return properties map to provide to [Qonversion.setUserProperties] method.
     */
    fun build(): Map<String, String> = properties
}
