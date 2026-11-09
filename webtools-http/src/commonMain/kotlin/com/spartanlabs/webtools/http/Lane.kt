package com.spartanlabs.webtools.http

/**
 * A class of callers that shares a budget's windows. Calls on one lane are served first come first served; a lane's share
 * (`WindowedCallBudget.Builder.laneShares`) is a floor held back for it and never lent.
 *
 * **Stable Core.** Equality is on [name], case-sensitively; [Interactive] and [Tracker] are ordinary lanes, not a closed set.
 *
 * @param name the lane's name; not blank.
 * @throws IllegalArgumentException if [name] is blank.
 */
public class Lane(
    /** The lane's name; not blank. */
    public val name: String,
) {
    init {
        require(name.isNotBlank()) { "name must not be blank" }
    }

    /** Two [Lane]s are equal when their [name]s are equal, case-sensitively. */
    override fun equals(other: Any?): Boolean = other is Lane && other.name == name
    /** Returns a hash code consistent with [equals]: it is computed from the same properties. */
    override fun hashCode(): Int = name.hashCode()
    /** A diagnostic string for logs: the lane's [name]. Its format is not part of the API and may change. */
    override fun toString(): String = "Lane($name)"

    /** The two lanes the stack names. */
    public companion object {
        /** Calls a person is waiting on; it holds a 20 % share by default. */
        public val Interactive: Lane = Lane("Interactive")

        /** Background tracking calls. */
        public val Tracker: Lane = Lane("Tracker")
    }
}
