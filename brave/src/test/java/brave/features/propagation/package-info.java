/**
 * Before, we had the same algorithm for encoding B3 copy pasted a couple times. We now have a
 * type {@link brave.propagation.Propagation} which supplies an implementation such as B3. This
 * includes common functions such as how to extract and inject based on map-like carriers.
 */
package brave.features.propagation;
