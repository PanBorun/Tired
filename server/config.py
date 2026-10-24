import os

# 阿里云百炼配置
BAILIAN_API_KEY = os.getenv(
    "BAILIAN_API_KEY",
    "sk-ws-H.PMHHPRM.6lTS.MEUCIBG7FCNn50Ln5PgHsrTQbivT8chYXcFO1uB1bHgmPaDIAiEA0nwV-yYrYQYZlurmpkoVPOW6QbP_Iaud54k5VQSN4bA"
)

BAILIAN_BASE_URL = os.getenv(
    "BAILIAN_BASE_URL",
    "https://ws-1zaff7zj8fap0ph2.cn-beijing.maas.aliyuncs.com/compatible-mode/v1"
)

CHAT_MODEL = os.getenv("CHAT_MODEL", "qwen-plus")
