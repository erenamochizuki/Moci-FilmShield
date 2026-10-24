package org.moci.filmshield.api;

/**
 * Marks a hullmod as a participant in surface-shield arbitration.
 * The first unsuppressed participant in ShipVariantAPI.getHullMods() wins.
 * Custom implementations must check FilmShield.isSelected before adding any effects.
 */
public interface FilmShieldProvider { }
