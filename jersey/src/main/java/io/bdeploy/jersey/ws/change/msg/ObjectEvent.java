package io.bdeploy.jersey.ws.change.msg;

/**
 * Determines what change happened in an {@link ObjectChangeDto}.
 */
public enum ObjectEvent {

    /**
     * The specified object has been created.
     */
    CREATED,

    /**
     * The specified object has changed. This typically refers to information closely attached to the specified object which is
     * usually fetched along with the object, making up the overall user-perception of the specified object. The details may hold
     * additional information.
     */
    CHANGED,

    /**
     * The object has been removed.
     */
    REMOVED

}
