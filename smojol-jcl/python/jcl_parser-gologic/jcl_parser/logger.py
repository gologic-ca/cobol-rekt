import logging
import sys
from enum import Enum
from typing import Optional

class LogLevel(Enum):
    INFO = "INFO"
    WARNING = "WARNING"
    DEBUG = "DEBUG"

class Logger:
    _instance = None
    _logger = None
    _current_level = LogLevel.INFO

    def __new__(cls):
        if cls._instance is None:
            cls._instance = super(Logger, cls).__new__(cls)
            cls._instance._setup_logger()
        return cls._instance

    def _setup_logger(self):
        # Remove any existing handlers
        if self._logger:
            for handler in self._logger.handlers[:]:
                self._logger.removeHandler(handler)

        self._logger = logging.getLogger("jcl_parser")
        self._logger.setLevel(logging.DEBUG)
        self._logger.propagate = False  # Prevent propagation to root logger
        
        # Create console handler
        console_handler = logging.StreamHandler(sys.stdout)
        console_handler.setLevel(logging.DEBUG)
        
        # Create formatter
        formatter = logging.Formatter('%(asctime)s - %(name)s - %(levelname)s - %(message)s')
        console_handler.setFormatter(formatter)
        
        # Add handler to logger
        self._logger.addHandler(console_handler)

    def set_level(self, level: LogLevel):
        """Set the logging level for the logger."""
        self._current_level = level
        if level == LogLevel.INFO:
            self._logger.setLevel(logging.INFO)
        elif level == LogLevel.WARNING:
            self._logger.setLevel(logging.WARNING)
        elif level == LogLevel.DEBUG:
            self._logger.setLevel(logging.DEBUG)

    def get_level(self) -> LogLevel:
        """Get the current logging level."""
        return self._current_level

    def info(self, message: str):
        """Log an info message."""
        if self._current_level in [LogLevel.INFO, LogLevel.DEBUG]:
            self._logger.info(message)

    def warning(self, message: str):
        """Log a warning message."""
        if self._current_level in [LogLevel.INFO, LogLevel.WARNING, LogLevel.DEBUG]:
            self._logger.warning(message)

    def debug(self, message: str):
        """Log a debug message."""
        if self._current_level == LogLevel.DEBUG:
            self._logger.debug(message)

# Create a singleton instance
logger = Logger()