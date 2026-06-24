package com.tirmizee.utility;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import com.tirmizee.model.CampaignFilterRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;
import org.springframework.data.elasticsearch.core.query.GeoDistanceOrder;

public class CampaignSearchHelper {

    // ==========================================
    // Helper 1: แปะเงื่อนไข Geo Distance แบบ Dynamic
    // ==========================================
    public static void applyGeoFilterIfPresent(BoolQuery.Builder b, CampaignFilterRequest request) {
        if (request.getUserLat() != null && request.getUserLon() != null) {
            // ถ้าไม่ได้ส่ง radius มา ให้ตั้งค่า Default เป็น 5 กิโลเมตร
            int radius = (request.getRadiusKm() != null && request.getRadiusKm() > 0) ? request.getRadiusKm() : 5;

            b.filter(f -> f.geoDistance(g -> g
                    .field("location")
                    .distance(radius + "km")
                    .location(loc -> loc.latlon(ll -> ll
                            .lat(request.getUserLat())
                            .lon(request.getUserLon())
                    ))
            ));
        }
    }

    // ==========================================
    // Helper 2: สร้างเงื่อนไขการเรียงลำดับ
    // ==========================================
    public static Sort getSortLogic(CampaignFilterRequest request) {
        // 1. Priority สูงสุด: ตรวจสอบว่ามีการส่งพิกัดมาหรือไม่
        // ถ้ามีพิกัดมา บังคับเรียงจาก "ใกล้ที่สุดไปไกลที่สุด" (ASC) โดยไม่สน sortId เดิม
        if (request.getUserLat() != null && request.getUserLon() != null) {
            GeoPoint userLocation = new GeoPoint(request.getUserLat(), request.getUserLon());

            return Sort.by(
                    new GeoDistanceOrder("location", userLocation)
                            .withUnit("km") // (Optional) กำหนดหน่วยให้ชัดเจน
            );
        }

        // 2. Fallback: ถ้าไม่มีพิกัด (เช่น ปิด GPS หรือค้นหาปกติ) ให้กลับไปใช้ Logic เดิม
        if (request.getSortId() == null) return Sort.unsorted();
        return switch (request.getSortId()) {
            case "1" -> Sort.by(Sort.Direction.DESC, "effectiveDate");
            case "2" -> Sort.by(Sort.Direction.ASC, "expireDate");
            default -> Sort.unsorted();
        };
    }

}
