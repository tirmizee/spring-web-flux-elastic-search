package com.tirmizee.document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(indexName = "promotions")
@Setting(settingPath = "elastic-settings.json")
public class PromotionDocument {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String language; // เก็บค่า "th" หรือ "en"

    @MultiField(
            mainField = @Field(type = FieldType.Text),
            otherFields = {
                    @InnerField(suffix = "th", type = FieldType.Text, analyzer = "thai_html_analyzer"),
                    @InnerField(suffix = "en", type = FieldType.Text, analyzer = "en_html_analyzer")
            }
    )
    private String name;

    @MultiField(
            mainField = @Field(type = FieldType.Text),
            otherFields = {
                    @InnerField(suffix = "th", type = FieldType.Text, analyzer = "thai_html_analyzer"),
                    @InnerField(suffix = "en", type = FieldType.Text, analyzer = "en_html_analyzer")
            }
    )
    private String detail;

    // === Field อื่นๆ เหมือนเดิม ===
    @Field(type = FieldType.Keyword)
    private String link;

    @Field(type = FieldType.Date)
    private Instant effectiveDate;

    @Field(type = FieldType.Date)
    private Instant expireDate;

    @Field(type = FieldType.Keyword)
    private String promotionCode;

    @Field(type = FieldType.Keyword)
    private String type;

    @Field(type = FieldType.Keyword)
    private String registrationType;

    @Field(type = FieldType.Integer)
    private Integer mechanic;

    @Field(type = FieldType.Keyword, index = false)
    private String displayImageSmall;

    @Field(type = FieldType.Keyword, index = false)
    private String displayImageLarge;

    @GeoPointField
    private GeoPoint location; // ฟิลด์ใหม่สำหรับเก็บพิกัดละติจูด/ลองจิจูด
}
