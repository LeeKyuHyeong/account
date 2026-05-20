/// 영수증 업로드 응답 (백엔드 [ReceiptController.AnalyzeResponse] 와 1:1).
class ReceiptUploadResponse {
  const ReceiptUploadResponse({
    required this.receiptId,
    required this.transactionId,
    required this.analysis,
    required this.autoConfirmable,
    required this.requiresManualClassification,
  });

  factory ReceiptUploadResponse.fromJson(Map<String, dynamic> json) {
    return ReceiptUploadResponse(
      receiptId: (json['receiptId'] as num).toInt(),
      transactionId: (json['transactionId'] as num).toInt(),
      analysis: ReceiptAnalysis.fromJson(
        json['analysis'] as Map<String, dynamic>,
      ),
      autoConfirmable: json['autoConfirmable'] as bool,
      requiresManualClassification:
          json['requiresManualClassification'] as bool,
    );
  }

  final int receiptId;
  final int transactionId;
  final ReceiptAnalysis analysis;

  /// 신뢰도 ≥ 0.8 — 클라이언트가 별도 컨펌 없이 CONFIRMED 로 승격 가능.
  final bool autoConfirmable;

  /// 신뢰도 < 0.5 — 사용자에게 카테고리 수동 선택을 요구해야 한다.
  final bool requiresManualClassification;
}

/// Claude 분석 결과 (백엔드 [ReceiptAnalysisResult] 와 1:1).
class ReceiptAnalysis {
  const ReceiptAnalysis({
    required this.merchant,
    required this.category,
    required this.total,
    required this.confidence,
    this.date,
    this.merchantType,
    this.subcategory,
    this.paymentMethod,
  });

  factory ReceiptAnalysis.fromJson(Map<String, dynamic> json) {
    return ReceiptAnalysis(
      date: json['date'] != null ? DateTime.parse(json['date'] as String) : null,
      merchant: json['merchant'] as String,
      merchantType: json['merchant_type'] as String?,
      category: json['category'] as String,
      subcategory: json['subcategory'] as String?,
      total: (json['total'] as num).toDouble(),
      paymentMethod: json['payment_method'] as String?,
      confidence: (json['confidence'] as num).toDouble(),
    );
  }

  final DateTime? date;
  final String merchant;
  final String? merchantType;
  final String category;
  final String? subcategory;
  final double total;
  final String? paymentMethod;
  final double confidence;
}
