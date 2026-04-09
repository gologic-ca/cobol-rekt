import pytest
import io
import sys
import logging
from jcl_parser.logger import logger, LogLevel

class LogCapture:
    def __init__(self):
        self.stream = io.StringIO()
        self.handler = logging.StreamHandler(self.stream)
        self.handler.setFormatter(logging.Formatter('%(asctime)s - %(name)s - %(levelname)s - %(message)s'))

    def __enter__(self):
        logger._logger.addHandler(self.handler)
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        logger._logger.removeHandler(self.handler)

    def get_output(self):
        return self.stream.getvalue()

def test_logger_singleton():
    """Test that logger is a singleton"""
    logger1 = logger
    logger2 = logger
    assert logger1 is logger2

def test_log_levels():
    """Test all log levels"""
    # Test INFO level
    with LogCapture() as capture:
        logger.set_level(LogLevel.INFO)
        logger.info("Test info message")
        logger.warning("Test warning message")
        logger.debug("Test debug message")
        output = capture.get_output()
        assert "Test info message" in output
        assert "Test warning message" in output
        assert "Test debug message" not in output

    # Test warning level
    with LogCapture() as capture:
        logger.set_level(LogLevel.WARNING)
        logger.info("Test info message")
        logger.warning("Test warning message")
        logger.debug("Test debug message")
        output = capture.get_output()
        assert "Test info message" not in output  # INFO messages should not appear when level is WARNING
        assert "Test warning message" in output
        assert "Test debug message" not in output

    # Test DEBUG level
    with LogCapture() as capture:
        logger.set_level(LogLevel.DEBUG)
        logger.info("Test info message")
        logger.warning("Test warning message")
        logger.debug("Test debug message")
        output = capture.get_output()
        assert "Test info message" in output
        assert "Test warning message" in output
        assert "Test debug message" in output

def test_log_format():
    """Test log message format"""
    with LogCapture() as capture:
        logger.set_level(LogLevel.INFO)
        test_message = "Test message"
        logger.info(test_message)
        output = capture.get_output()
        assert "jcl_parser" in output
        assert "INFO" in output
        assert test_message in output

def test_log_level_setting():
    """Test log level setting and getting"""
    logger.set_level(LogLevel.DEBUG)
    assert logger.get_level() == LogLevel.DEBUG
    
    logger.set_level(LogLevel.WARNING)
    assert logger.get_level() == LogLevel.WARNING
    
    logger.set_level(LogLevel.INFO)
    assert logger.get_level() == LogLevel.INFO