package com.heatic.satelliteskyradar;

public class SatelliteInfo {
    public int svid;
    public int constellationType;
    public float azimuth;
    public float elevation;
    public boolean usedInFix;
    public float cn0DbHz;
    public float rangeRateMps; // pseudorange rate (m/s), positive = moving away

    public SatelliteInfo(int svid, int constellationType, float azimuth, float elevation,
                         boolean usedInFix, float cn0DbHz, float rangeRateMps) {
        this.svid = svid;
        this.constellationType = constellationType;
        this.azimuth = azimuth;
        this.elevation = elevation;
        this.usedInFix = usedInFix;
        this.cn0DbHz = cn0DbHz;
        this.rangeRateMps = rangeRateMps;
    }

    public String getName() {
        switch (constellationType) {
            case android.location.GnssStatus.CONSTELLATION_GPS: return "G" + svid;
            case android.location.GnssStatus.CONSTELLATION_GLONASS: return "R" + svid;
            case android.location.GnssStatus.CONSTELLATION_BEIDOU: return "C" + svid;
            case android.location.GnssStatus.CONSTELLATION_GALILEO: return "E" + svid;
            case android.location.GnssStatus.CONSTELLATION_QZSS: return "J" + svid;
            case android.location.GnssStatus.CONSTELLATION_IRNSS: return "I" + svid;
            case android.location.GnssStatus.CONSTELLATION_SBAS: return "S" + svid;
            default: return "U" + svid;
        }
    }
}