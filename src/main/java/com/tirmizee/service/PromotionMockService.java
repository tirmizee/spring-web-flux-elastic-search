package com.tirmizee.service;

import com.tirmizee.document.PromotionDocument;
import com.tirmizee.repository.PromotionDocumentSearchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class PromotionMockService {

    private final PromotionDocumentSearchRepository promotionDocumentSearchRepository;

    // --- Mock Data Dictionary สำหรับสุ่มให้สมจริงและมีความหมายตรงกันทั้ง TH และ EN ---
    private static final String[] BRANDS = {
            "Shopee", "Lazada", "GrabFood", "LINE MAN", "Agoda",
            "Starbucks", "Major Cineplex", "Central", "Watsons", "PTT Station",
            "IKEA", "Uniqlo", "Foodpanda", "Traveloka", "Makro"
    };

    private static final String[] BENEFITS_TH = {
            "รับส่วนลดสูงสุด 20%", "รับเครดิตเงินคืน 15%", "รับคะแนนสะสม 5 เท่า",
            "ซื้อ 1 แถม 1", "ลดทันที 100 บาท", "รับสิทธิ์ส่งฟรี", "แลกรับส่วนลด 500 บาท"
    };
    private static final String[] BENEFITS_EN = {
            "Get up to 20% off", "Get 15% Cash Back", "Earn 5X Points",
            "Buy 1 Get 1 Free", "Instant 100 THB discount", "Get Free Delivery", "Redeem for 500 THB off"
    };

    private static final String[] CONDITIONS_TH = {
            "เมื่อใช้จ่ายผ่านบัตร", "เมื่อช้อปครบ 2,000 บาท", "สำหรับลูกค้าใหม่",
            "ทุกวันเสาร์-อาทิตย์", "เฉพาะรายการออนไลน์", "เมื่อแลกคะแนนสะสมเท่ายอดซื้อ"
    };
    private static final String[] CONDITIONS_EN = {
            "when spending with credit card", "on minimum spend of 2,000 THB", "for new customers",
            "every weekend", "for online purchases only", "when redeeming points equivalent to spend"
    };

    public Mono<String> generateAndInsertMockData() {
        Instant now = Instant.now();
        List<MockTemplate> specificCampaigns = getSpecificCampaigns();

        // 1. สร้าง Flux จากข้อมูลจริง (Specific Data)
        Flux<PromotionDocument> specificDataFlux = Flux.fromIterable(specificCampaigns)
                .flatMap(template -> Flux.just(
                        createFromTemplate(template, "th"),
                        createFromTemplate(template, "en")
                ));

        // 2. สร้าง Flux สำหรับข้อมูลสุ่มที่เหลือให้ครบ 1000
        int remainingCount = 1000 - specificCampaigns.size();

        Flux<PromotionDocument> randomDataFlux = Flux.range(specificCampaigns.size() + 1, remainingCount)
                .flatMap(i -> {
                    String sharedCampaignCode = String.format("C%06d", i);
                    String baseId = String.valueOf(8000 + i);

                    ThreadLocalRandom random = ThreadLocalRandom.current();
                    int brandIdx = random.nextInt(BRANDS.length);
                    int benefitIdx = random.nextInt(BENEFITS_TH.length);
                    int conditionIdx = random.nextInt(CONDITIONS_TH.length);

                    String brand = BRANDS[brandIdx];

                    // สร้างข้อมูลที่แมตช์กันทั้งสองภาษา
                    String nameTh = String.format("%s: %s %s", brand, BENEFITS_TH[benefitIdx], CONDITIONS_TH[conditionIdx]);
                    String detailTh = String.format("<p>สิทธิพิเศษเฉพาะคุณที่ <b>%s</b> %s %s (รหัสอ้างอิง: %s)</p>",
                            brand, BENEFITS_TH[benefitIdx], CONDITIONS_TH[conditionIdx], sharedCampaignCode);

                    String nameEn = String.format("%s: %s %s", brand, BENEFITS_EN[benefitIdx], CONDITIONS_EN[conditionIdx]);
                    String detailEn = String.format("<p>Exclusive privilege at <b>%s</b>. %s %s. (Ref: %s)</p>",
                            brand, BENEFITS_EN[benefitIdx], CONDITIONS_EN[conditionIdx], sharedCampaignCode);

                    // สุ่มพิกัดรอบๆ กรุงเทพมหานคร
                    double randomLat = 13.5 + (random.nextDouble() * 0.4);
                    double randomLon = 100.3 + (random.nextDouble() * 0.5);
                    GeoPoint randomLocation = new GeoPoint(randomLat, randomLon);

                    PromotionDocument docTh = createRandomDocument(baseId, "th", sharedCampaignCode, nameTh, detailTh, randomLocation, now, brand);
                    PromotionDocument docEn = createRandomDocument(baseId, "en", sharedCampaignCode, nameEn, detailEn, randomLocation, now, brand);

                    return Flux.just(docTh, docEn);
                });

        // 3. Concat และ Save เข้า Elasticsearch โดยแบ่งเป็น Batch เพื่อประสิทธิภาพของ WebFlux
        return Flux.concat(specificDataFlux, randomDataFlux)
                .buffer(100)
                .flatMap(promotionDocumentSearchRepository::saveAll)
                .then(Mono.just("Successfully inserted Mock promotions (Specific + Realistic Random) for TH and EN!"));
    }

    // Method สำหรับสร้าง Document จากการสุ่ม
    private PromotionDocument createRandomDocument(String baseId, String lang, String code, String name, String detail, GeoPoint location, Instant now, String brand) {
        PromotionDocument doc = new PromotionDocument();
        doc.setId(baseId + "_" + lang);
        doc.setLanguage(lang);
        doc.setPromotionCode(code);
        doc.setName(name);
        doc.setDetail(detail);
        doc.setLocation(location);

        doc.setLink("https://www.cardx.co.th/credit-card/promotion/" + brand.toLowerCase().replace(" ", "-"));
        doc.setEffectiveDate(now.minus(ThreadLocalRandom.current().nextInt(1, 15), ChronoUnit.DAYS));
        doc.setExpireDate(now.plus(ThreadLocalRandom.current().nextInt(30, 90), ChronoUnit.DAYS));
        doc.setType("Mass");
        doc.setRegistrationType(ThreadLocalRandom.current().nextBoolean() ? "SMS" : "APP");
        doc.setMechanic(ThreadLocalRandom.current().nextInt(0, 3));

        doc.setDisplayImageSmall(String.format("https://dummyimage.com/500x167/0a4275/fff&text=%s+Promo", brand.replace(" ", "+")));
        doc.setDisplayImageLarge(String.format("https://dummyimage.com/1000x335/0a4275/fff&text=%s+Promo", brand.replace(" ", "+")));

        return doc;
    }

    // Method สำหรับแปลง Template จากข้อมูลจริงให้เป็น Document Object (แก้ไขให้สมบูรณ์แล้ว)
    private PromotionDocument createFromTemplate(MockTemplate template, String lang) {
        PromotionDocument doc = new PromotionDocument();
        doc.setId(template.id + "_" + lang);
        doc.setLanguage(lang);
        doc.setPromotionCode(template.code);
        doc.setLink(template.link);
        doc.setEffectiveDate(Instant.parse(template.effectiveDate));
        doc.setExpireDate(Instant.parse(template.expireDate));
        doc.setType(template.type);
        doc.setRegistrationType(template.registrationType);
        doc.setMechanic(template.mechanic);
        doc.setDisplayImageSmall(template.imgSmall);
        doc.setDisplayImageLarge(template.imgLarge);
        doc.setLocation(new GeoPoint(template.lat, template.lon));
        if ("th".equalsIgnoreCase(lang)) {
            doc.setName(template.nameTh);
            doc.setDetail(template.detailTh);
        } else {
            doc.setName(template.nameEn);
            doc.setDetail(template.detailEn);
        }
        return doc;
    }

    // --- Mock Data Template จากแคมเปญจริง ---
    private List<MockTemplate> getSpecificCampaigns() {
        return Arrays.asList(
                new MockTemplate(
                        "8996", "C6901188",
                        "รับคะแนนสะสมพิเศษ POINTX รวมสูงสุด 15 เท่า*", "Earn up to 15X extra POINTX rewards*",
                        "<p>จองยนตรกรรมในฝันภายในงาน MGC-ASIA MOBILITY EXPO 2026</p>", "<p>Reserve your dream car at MGC-ASIA MOBILITY EXPO 2026</p>",
                        "https://www.cardx.co.th/credit-card/promotion/mgc-asia-mobility-exop-jun26-usc04",
                        "2026-06-17T00:00:00.000Z", "2026-06-21T00:00:00.000Z", "Mass", "FOUR_DIGITS", 2,
                        "https://cdx-prod-ssc-frontend.cardx.co.th/small_b_mgc_asia_mobility_exop_jun26_usc04_10507c53f1.jpg",
                        "https://cdx-prod-ssc-frontend.cardx.co.th/large_b_mgc_asia_mobility_exop_jun26_usc04_10507c53f1.jpg",
                        13.7468, 100.5346
                ),
                new MockTemplate(
                        "8988", "C6901283",
                        "สมัครบัตรเครดิต SCB PRIME", "Apply for SCB PRIME Credit Card",
                        "<p>เพื่อเติมเต็มสุนทรีย์แห่งการใช้ชีวิต</p>", "<p>To fulfill the aesthetics of life</p>",
                        "https://www.cardx.co.th/credit-card/promotion/prime-acq-jun26-wec13",
                        "2026-06-15T00:00:00.000Z", "2026-09-30T00:00:00.000Z", "Mass", "OTHER", 0,
                        "https://cdx-prod-ssc-frontend.cardx.co.th/small_b_prime_acq_jun26_wec13_5aac0c46f5.jpg",
                        "https://cdx-prod-ssc-frontend.cardx.co.th/large_b_prime_acq_jun26_wec13_5aac0c46f5.jpg",
                        13.8283, 100.5615
                ),
                new MockTemplate(
                        "8998", "C6901253",
                        "ยกระดับทุกการออกรอบด้วยสิทธิพิเศษ ณ Nikanti Golf Club", "Elevate every round with privileges at Nikanti Golf Club",
                        "<p>รับส่วนลดสูงสุด 10%* รับเครดิตเงินคืนรวมสูงสุด 2,000 บาท**</p>", "<p>Get up to 10% discount* and cash back up to 2,000 THB**</p>",
                        "https://www.cardx.co.th/credit-card/promotion/nikanti-golf-club-jun26-usc03",
                        "2026-06-15T00:00:00.000Z", "2027-03-31T00:00:00.000Z", "Mass", "OTHER", 0,
                        "https://cdx-prod-ssc-frontend.cardx.co.th/small_b_nikanti_golf_club_jun26_usc03_55c70a6fa7.jpg",
                        "https://cdx-prod-ssc-frontend.cardx.co.th/large_b_nikanti_golf_club_jun26_usc03_55c70a6fa7.jpg",
                        13.8058, 100.0125
                ),
                new MockTemplate(
                        "9012", "C6901126",
                        "เรียกรถผ่านแอป Bolt", "Ride with Bolt Application",
                        "<p>รับส่วนลดรวมสูงสุด 160 บาท*</p>", "<p>Get total discounts up to 160 THB*</p>",
                        "https://www.cardx.co.th/credit-card/promotion/bolt-jun26-onc05",
                        "2026-06-10T00:00:00.000Z", "2026-08-31T00:00:00.000Z", "Mass", "TWELVE_DIGITS", 0,
                        "https://cdx-prod-ssc-frontend.cardx.co.th/small_b_bolt_jun26_onc05_337d594aed.jpg",
                        "https://cdx-prod-ssc-frontend.cardx.co.th/large_b_bolt_jun26_onc05_337d594aed.jpg",
                        13.7367, 100.5586
                ),
                new MockTemplate(
                        "8938", "C6901047",
                        "ช้อป Online ปักหมุดแลกรับคุ้ม", "Online Shopping, Pin and Redeem for Value",
                        "<p>แลกคะแนนรับเครดิตเงินคืนสูงสุด 20%*</p>", "<p>Redeem points for up to 20% cashback*</p>",
                        "https://www.cardx.co.th/credit-card/promotion/thematic-day-jun26-onc05",
                        "2026-06-01T00:00:00.000Z", "2026-12-31T00:00:00.000Z", "Mass", "POINT_BURN", 0,
                        "https://cdx-prod-ssc-frontend.cardx.co.th/small_b_thematic_day_jun26_onc05_cc68313ede.jpg",
                        "https://cdx-prod-ssc-frontend.cardx.co.th/large_b_thematic_day_jun26_onc05_cc68313ede.jpg",
                        13.7267, 100.5106
                )
        );
    }

    // Inner class สำหรับเก็บข้อมูลดิบก่อนนำไปสร้าง Document
    private static class MockTemplate {
        String id; String code;
        String nameTh; String nameEn;
        String detailTh; String detailEn;
        String link; String effectiveDate; String expireDate;
        String type; String registrationType; int mechanic;
        String imgSmall; String imgLarge;
        double lat; double lon; // เพิ่มฟิลด์พิกัด

        public MockTemplate(String id, String code, String nameTh, String nameEn, String detailTh, String detailEn, String link, String effectiveDate, String expireDate, String type, String registrationType, int mechanic, String imgSmall, String imgLarge, double lat, double lon) {
            this.id = id; this.code = code;
            this.nameTh = nameTh; this.nameEn = nameEn;
            this.detailTh = detailTh; this.detailEn = detailEn;
            this.link = link; this.effectiveDate = effectiveDate; this.expireDate = expireDate;
            this.type = type; this.registrationType = registrationType; this.mechanic = mechanic;
            this.imgSmall = imgSmall; this.imgLarge = imgLarge;
            this.lat = lat; this.lon = lon;
        }
    }
}
