import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:image/image.dart' as img;
import 'package:image_picker/image_picker.dart';

import '../data/receipt_api.dart';

/// 영수증 촬영 / 갤러리 선택 → 압축 → 업로드 진입 화면.
///
/// 압축 정책 (docs/account.md §3.2): 클라이언트 측에서 긴 변 1280px 이하로 리사이즈,
/// JPEG 품질 80%. 서버는 추가 처리 없이 원본 그대로 저장 + Claude 분석.
class ReceiptCaptureScreen extends ConsumerStatefulWidget {
  const ReceiptCaptureScreen({super.key});

  @override
  ConsumerState<ReceiptCaptureScreen> createState() =>
      _ReceiptCaptureScreenState();
}

class _ReceiptCaptureScreenState extends ConsumerState<ReceiptCaptureScreen> {
  static const int _maxDimension = 1280;
  static const int _jpegQuality = 80;

  final _picker = ImagePicker();
  bool _busy = false;
  String? _statusMessage;
  String? _errorMessage;

  Future<void> _pickAndUpload(ImageSource source) async {
    setState(() {
      _busy = true;
      _errorMessage = null;
      _statusMessage = '이미지 선택 중...';
    });

    try {
      final picked = await _picker.pickImage(
        source: source,
        maxWidth: _maxDimension.toDouble(),
        maxHeight: _maxDimension.toDouble(),
        imageQuality: _jpegQuality,
        requestFullMetadata: false,
      );
      if (picked == null) {
        setState(() {
          _busy = false;
          _statusMessage = null;
        });
        return;
      }

      setState(() => _statusMessage = '이미지 압축 중...');
      final original = await picked.readAsBytes();
      final compressed = await _compressOffMainThread(original);

      setState(() => _statusMessage = 'Claude 분석 중...');
      final response = await ref.read(receiptApiProvider).upload(
            bytes: compressed,
            filename: 'receipt.jpg',
            contentType: 'image/jpeg',
          );

      if (!mounted) return;
      context.pushReplacement('/receipts/confirm', extra: response);
    } on DioException catch (e) {
      setState(() => _errorMessage = _formatDioError(e));
    } catch (e) {
      setState(() => _errorMessage = '실패: $e');
    } finally {
      if (mounted) {
        setState(() {
          _busy = false;
          _statusMessage = null;
        });
      }
    }
  }

  /// 큰 이미지 디코딩/인코딩은 메인 스레드를 막을 수 있으므로 isolate 로 격리.
  Future<List<int>> _compressOffMainThread(Uint8List bytes) {
    return compute(_compressJpeg, bytes);
  }

  static List<int> _compressJpeg(Uint8List bytes) {
    final decoded = img.decodeImage(bytes);
    if (decoded == null) {
      // 디코딩 실패 시 원본 그대로 (서버 측 검증으로 처리).
      return bytes;
    }
    final longest = decoded.width > decoded.height ? decoded.width : decoded.height;
    final resized = longest <= _maxDimension
        ? decoded
        : img.copyResize(
            decoded,
            width: decoded.width >= decoded.height ? _maxDimension : null,
            height: decoded.height > decoded.width ? _maxDimension : null,
            interpolation: img.Interpolation.linear,
          );
    return img.encodeJpg(resized, quality: _jpegQuality);
  }

  String _formatDioError(DioException e) {
    final data = e.response?.data;
    if (data is Map<String, dynamic>) {
      final message = data['message'] as String?;
      if (message != null) return message;
    }
    return e.message ?? '서버 호출 실패';
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('영수증 촬영')),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const Spacer(),
              const Icon(Icons.receipt_long, size: 96),
              const SizedBox(height: 24),
              Text(
                '영수증을 카메라로 찍거나 갤러리에서 선택하세요.',
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.titleMedium,
              ),
              const SizedBox(height: 8),
              Text(
                '업로드 전 1280px / JPEG 80% 로 압축됩니다.',
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.bodySmall,
              ),
              const Spacer(),
              if (_statusMessage != null) ...[
                const LinearProgressIndicator(),
                const SizedBox(height: 12),
                Text(_statusMessage!, textAlign: TextAlign.center),
                const SizedBox(height: 24),
              ],
              if (_errorMessage != null) ...[
                Text(
                  _errorMessage!,
                  textAlign: TextAlign.center,
                  style: TextStyle(color: Theme.of(context).colorScheme.error),
                ),
                const SizedBox(height: 24),
              ],
              FilledButton.icon(
                onPressed: _busy ? null : () => _pickAndUpload(ImageSource.camera),
                icon: const Icon(Icons.camera_alt),
                label: const Text('카메라로 촬영'),
              ),
              const SizedBox(height: 12),
              OutlinedButton.icon(
                onPressed: _busy ? null : () => _pickAndUpload(ImageSource.gallery),
                icon: const Icon(Icons.photo_library),
                label: const Text('갤러리에서 선택'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
