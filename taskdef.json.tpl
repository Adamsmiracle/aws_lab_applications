{
  "family": "${PROJECT_NAME}-task",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "${TASK_CPU}",
  "memory": "${TASK_MEMORY}",
  "executionRoleArn": "arn:aws:iam::${ACCOUNT_ID}:role/${PROJECT_NAME}-task-exec-role",
  "taskRoleArn": "arn:aws:iam::${ACCOUNT_ID}:role/${PROJECT_NAME}-task-role",
  "runtimePlatform": {
    "operatingSystemFamily": "LINUX",
    "cpuArchitecture": "X86_64"
  },
  "containerDefinitions": [
    {
      "name": "${CONTAINER_NAME}",
      "image": "<IMAGE1_NAME>",
      "essential": true,
      "portMappings": [
        { "containerPort": ${CONTAINER_PORT}, "protocol": "tcp" }
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/${PROJECT_NAME}",
          "awslogs-region": "${AWS_REGION}",
          "awslogs-stream-prefix": "${CONTAINER_NAME}"
        }
      }
    }
  ]
}
