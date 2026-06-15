import re

with open("app/src/main/java/com/arcvideo/opencvstudy/ArcQRDetecter.java", "r") as f:
    content = f.read()

# 1. Add Huawei Scan imports
if "import com.huawei.hms.hmsscankit.ScanUtil;" not in content:
    content = content.replace("import com.king.wechat.qrcode.WeChatQRCodeDetector;", "import com.king.wechat.qrcode.WeChatQRCodeDetector;\nimport com.huawei.hms.hmsscankit.ScanUtil;\nimport com.huawei.hms.ml.scan.HmsScan;\nimport com.huawei.hms.ml.scan.HmsScanAnalyzerOptions;\nimport android.content.Context;")

# 2. Add DECODE_HUAWEI
if "DECODE_HUAWEI = 4;" not in content:
    content = content.replace("public static final int DECODE_OPENCV_DETECT = 3;", "public static final int DECODE_OPENCV_DETECT = 3;\n    public static final int DECODE_HUAWEI = 4;")

# 3. Add Context to parseQRcode
content = re.sub(r'public static ResultPoint\[\] parseQRcode\(Bitmap bitmap, int decodeType\)', 'public static ResultPoint[] parseQRcode(Context context, Bitmap bitmap, int decodeType)', content)
content = re.sub(r'resPoints = parseQRcode\(bitmap, decodeType\);', 'resPoints = parseQRcode(null, bitmap, decodeType);', content) # In detectQRCode (which we might not use directly)

# 4. Add Huawei case in parseQRcode
huawei_case = """        } else if (decodeType == DECODE_HUAWEI) {
            try {
                if (context != null) {
                    HmsScanAnalyzerOptions options = new HmsScanAnalyzerOptions.Creator()
                            .setHmsScanTypes(HmsScan.ALL_SCAN_TYPE)
                            .setPhotoMode(true)
                            .create();
                    HmsScan[] hmsScans = ScanUtil.decodeWithBitmap(context, bitmap, options);
                    if (hmsScans != null && hmsScans.length > 0) {
                        android.graphics.Point[] corners = hmsScans[0].getCornerPoints();
                        if (corners != null && corners.length >= 4) {
                            resPoints = new ResultPoint[4];
                            for (int i = 0; i < 4; i++) {
                                resPoints[i] = new ResultPoint(corners[i].x, corners[i].y);
                            }
                            Log.d(TAG, "parseQRcode out Huawei detect successful text = " + hmsScans[0].originalValue);
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Huawei error", e);
            }
        }"""
content = re.sub(r'\}\s*catch\s*\(Exception e\)\s*\{\s*Log\.e\(TAG, "OpenCV Detect error", e\);\s*\}\s*\}', '} catch (Exception e) { Log.e(TAG, "OpenCV Detect error", e); } }\n' + huawei_case, content)


# 5. Modify testAllDecodersAndGenerateQR
content = re.sub(r'public static Object\[\] testAllDecodersAndGenerateQR\(Bitmap bitmap\)', 'public static Object[] testAllDecodersAndGenerateQR(Context context, Bitmap bitmap)', content)

# 6. Add Huawei test in testAllDecodersAndGenerateQR
huawei_test = """
        // 4. Huawei Scan Kit
        start = System.currentTimeMillis();
        boolean huaweiSuccess = false;
        long huaweiGenTime = 0;
        Bitmap huaweiQr = null;
        String huaweiText = null;
        try {
            if (context != null) {
                HmsScanAnalyzerOptions options = new HmsScanAnalyzerOptions.Creator()
                        .setHmsScanTypes(HmsScan.ALL_SCAN_TYPE)
                        .setPhotoMode(true)
                        .create();
                HmsScan[] hmsScans = ScanUtil.decodeWithBitmap(context, bitmap, options);
                if (hmsScans != null && hmsScans.length > 0 && hmsScans[0].originalValue != null && !hmsScans[0].originalValue.isEmpty()) {
                    huaweiSuccess = true;
                    huaweiText = hmsScans[0].originalValue;
                }
            }
        } catch (Exception e) {}
        long huaweiTime = System.currentTimeMillis() - start;
        
        if (huaweiSuccess) {
            long genStart = System.currentTimeMillis();
            try {
                huaweiQr = CodeUtils.createQRCode(huaweiText, qrSize, null);
            } catch (Exception e) {}
            huaweiGenTime = System.currentTimeMillis() - genStart;
            resultStr.append("Huawei: 成功 (解码:").append(huaweiTime).append("ms, 生成:").append(huaweiGenTime).append("ms)\\n");
        } else {
            resultStr.append("Huawei: 失败 (").append(huaweiTime).append("ms)\\n");
        }
        
        if (zxingText != null) {
            resultStr.append("\\nZXing内容: ").append(zxingText);
        }
        if (zbarText != null && !zbarText.equals(zxingText)) {
            resultStr.append("\\nZBar内容: ").append(zbarText);
        }
        if (wechatText != null && !wechatText.equals(zxingText) && !wechatText.equals(zbarText)) {
            resultStr.append("\\nWeChat内容: ").append(wechatText);
        }
        if (huaweiText != null && !huaweiText.equals(zxingText) && !huaweiText.equals(zbarText) && !huaweiText.equals(wechatText)) {
            resultStr.append("\\nHuawei内容: ").append(huaweiText);
        }
        
        return new Object[] {resultStr.toString(), zxingQr, zbarQr, wechatQr, huaweiQr};
"""
# Replace the end of testAllDecodersAndGenerateQR
content = re.sub(r'if \(zxingText \!= null\) \{.*?return new Object\[\] \{resultStr\.toString\(\), zxingQr, zbarQr, wechatQr\};\s*\}', huawei_test + '\n    }', content, flags=re.DOTALL)


with open("app/src/main/java/com/arcvideo/opencvstudy/ArcQRDetecter.java", "w") as f:
    f.write(content)

