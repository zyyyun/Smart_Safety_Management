"""yolov7_fork — D:/2025_산업안전/산업안전/모델 7종/쓰러짐 탐지/ 의 models/ · utils/ 를 그대로 복사.

체크포인트(`yolov7-w6-pose.pt`)가 피클 경로에 `models.yolo.Model` 등을 내장하므로
폴더명을 바꾸지 않는다. fall_detector.py 에서:

    sys.path.insert(0, str(Path(__file__).resolve().parent / "yolov7_fork"))

로 yolov7_fork/ 자체를 sys.path 맨 앞에 추가한 뒤 `from models.yolo import Model`
과 같은 upstream 절대 import가 그대로 해석되도록 한다.
"""
