resource "aws_lb" "main" {
  # An ALB bills per hour whether or not it serves traffic, and it can't be stopped -- only deleted.
  count = var.paused ? 0 : 1

  name               = "${var.name_prefix}-alb"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = aws_subnet.public[*].id

  tags = { Project = var.name_prefix }
}

resource "aws_lb_target_group" "backend" {
  count = var.paused ? 0 : 1

  name        = "${var.name_prefix}-backend-tg"
  port        = var.container_port
  protocol    = "HTTP"
  vpc_id      = aws_vpc.main.id
  target_type = "ip"

  health_check {
    path                = "/actuator/health"
    matcher             = "200"
    interval            = 30
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }

  tags = { Project = var.name_prefix }
}

resource "aws_lb_listener" "http" {
  count = var.paused ? 0 : 1

  load_balancer_arn = aws_lb.main[0].arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.backend[0].arn
  }
}
