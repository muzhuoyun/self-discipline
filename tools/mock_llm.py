# -*- coding: utf-8 -*-
"""
本地 mock 大模型服务（OpenAI 兼容 /chat/completions，SSE 流式）。
用于端到端验证「三戒三修」App 的 AI 功能，无需真实大模型。

启动：python tools/mock_llm.py            （监听 0.0.0.0:8080）
App 设置里填：http://10.0.2.2:8080/v1   （模拟器访问宿主机）

按请求内容返回不同结果：
- 含「评分数据」+「规则总结」→ 打卡短评（流式中文文本）
- 含「当天实际情况描述」→ AI 辅助判断 JSON（items/level）
- 含「逐日评分记录」→ 周报/月报（流式文本）
- 含「已有成就」→ 成就 JSON
"""
import json
import time
from http.server import BaseHTTPRequestHandler, HTTPServer


def stream_sse(handler, text, delay=0.03):
    """把文本按字符切碎，模拟真实流式输出"""
    for ch in text:
        data = json.dumps({"choices": [{"delta": {"content": ch}}]})
        handler.wfile.write(f"data: {data}\n\n".encode("utf-8"))
        handler.wfile.flush()
        time.sleep(delay)
    handler.wfile.write(b"data: [DONE]\n\n")
    handler.wfile.flush()


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        if not self.path.endswith("/chat/completions"):
            self.send_error(404)
            return
        length = int(self.headers.get("Content-Length", 0))
        body = json.loads(self.rfile.read(length) or b"{}")
        messages = body.get("messages", [])
        user_msg = next((m["content"] for m in reversed(messages) if m["role"] == "user"), "")
        system_msg = next((m["content"] for m in reversed(messages) if m["role"] == "system"), "")
        print(f"REQ user_msg={user_msg[:60]!r}", flush=True)

        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.send_header("Cache-Control", "no-cache")
        self.end_headers()

        if "当天实际情况描述" in user_msg:
            # AI 辅助判断（详情页）：按「」中的类目标题精确匹配
            if "「戒淫」" in user_msg:
                reply = json.dumps({
                    "level": 8,
                    "reason": "从描述看有冲动但成功克制，符合 8 分档",
                }, ensure_ascii=False)
            else:
                reply = json.dumps({
                    "items": [
                        {"index": 0, "checked": True, "reason": "三餐都按时吃了"},
                        {"index": 1, "checked": True, "reason": "晚饭七八分饱，没有吃撑"},
                        {"index": 2, "checked": False, "reason": "提到喝了一杯奶茶，属于高糖饮料"},
                        {"index": 3, "checked": True, "reason": "没有暴饮暴食"},
                    ]
                }, ensure_ascii=False)
        elif "已有成就" in user_msg:
            # AI 添加成就
            reply = json.dumps({
                "achievements": [
                    {
                        "emoji": "🌊",
                        "title": "总分连续14天50+",
                        "description": "总分连续 14 天保持 50 分以上",
                        "metric": "TOTAL",
                        "window": "STREAK",
                        "target_value": 50,
                        "window_days": 14,
                    },
                    {
                        "emoji": "⚡",
                        "title": "戒贪累计30天满分",
                        "description": "累计 30 天戒贪拿到满分",
                        "metric": "JIE_TAN",
                        "window": "CUMULATIVE",
                        "target_value": 10,
                        "window_days": 30,
                    },
                    {
                        "emoji": "😈",
                        "title": "非法成就测试",
                        "description": "这个字段应该被校验拒绝",
                        "metric": "HACK",
                        "window": "WEEK",
                        "target_value": -5,
                        "window_days": 999,
                    },
                ]
            }, ensure_ascii=False)
        elif "逐日评分记录" in user_msg:
            # 周报 / 月报
            head = "本周" if "周报" in system_msg else "本月"
            reply = (
                f"这是{head}的 AI 报告（模拟数据）。\n"
                "【总体表现】这一周期你保持了稳定的打卡节奏，平均分稳中有升，"
                "说明坚持已经在起作用了。\n"
                "【亮点与问题】戒馋和修养是你的强项，稳定性很好；"
                "戒贪和修行偶有波动，可能是环境变化导致的。\n"
                "【建议】下周试着把「不长时间刷无意义内容」守住三天，"
                "你会发现时间多出很多。"
            )
        else:
            # 打卡短评
            reply = (
                "今天做得不错！总分已经超过了昨天，说明你在认真地对待每一天。"
                "戒馋是你的加分项，继续保持这种分寸感。"
                "明天可以试着在「修行」上加把劲，先专注完成最重要的一件事。"
                "慢慢来，每一步都算数。"
            )

        stream_sse(self, reply)

    def log_message(self, fmt, *args):
        pass


if __name__ == "__main__":
    print("Mock LLM listening on 0.0.0.0:8080 ...")
    HTTPServer(("0.0.0.0", 8080), Handler).serve_forever()
