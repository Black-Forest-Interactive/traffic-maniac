package de.bfi.trafficmaniac.osmimport

/** Kommandozeilenwerkzeug: OSM-Extrakt → Stadtdatei. Der eigentliche Import folgt in M1. */
fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Aufruf: osm-import <eingabe.osm.pbf> <ausgabe.city>")
        return
    }
    println("Import ist noch nicht implementiert (folgt in Meilenstein M1).")
}
