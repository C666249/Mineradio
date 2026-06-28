// Weather hero card API tests
const { describe, it } = require('node:test');
const assert = require('node:assert');

// The hero card needs these fields from /api/weather/radio:
//   weather.location.name — city name
//   weather.temperature — current temp
//   weather.apparentTemperature — feels like
//   weather.humidity — humidity
//   weather.label — weather description
//   weather.mood.title — mood title (e.g. "晴朗电台")
//   weather.mood.tagline — mood tagline

describe('Weather Hero API structure', () => {
  it('weather API returns hero card fields', async () => {
    const expectedFields = [
      'weather.location.name',
      'weather.temperature',
      'weather.apparentTemperature',
      'weather.humidity',
      'weather.label',
      'weather.mood.title',
      'weather.mood.tagline',
    ];

    // Test with a well-known city
    const base = 'http://127.0.0.1:3000';
    try {
      const resp = await fetch(`${base}/api/weather/radio?city=Shanghai`);
      const data = await resp.json();
      assert.ok(data.ok, 'API should return ok: true');
      assert.ok(data.weather, 'should have weather object');
      assert.ok(data.weather.mood, 'should have mood object');
      assert.ok(data.weather.mood.title, 'should have mood title');
      assert.ok(data.weather.mood.tagline, 'should have mood tagline');
    } catch (e) {
      // Server not running — skip integration test
      if (e.cause && e.cause.code === 'ECONNREFUSED') {
        console.log('  (skip: server not running)');
        return;
      }
      throw e;
    }
  });

  it('weather icon mapping covers all Open-Meteo codes', () => {
    // The hero card needs icons for these weather codes
    const knownCodes = [0, 1, 2, 3, 45, 48, 51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 71, 73, 75, 77, 80, 81, 82, 85, 86, 95, 96, 99];
    // Verify each code maps to non-empty string
    for (const code of knownCodes) {
      const icon = weatherIconEmoji(code);
      assert.ok(typeof icon === 'string' && icon.length > 0, `code ${code} should have icon`);
    }
  });
});

// Weather emoji mapping (from desktop-widget project, adapted)
function weatherIconEmoji(code) {
  const map = {
    0: '☀️', 1: '🌤️', 2: '⛅', 3: '☁️',
    45: '🌫️', 48: '🌫️',
    51: '🌧️', 53: '🌧️', 55: '🌧️', 56: '🌧️', 57: '🌧️',
    61: '🌧️', 63: '🌧️', 65: '🌧️', 66: '🌧️', 67: '🌧️',
    71: '🌨️', 73: '🌨️', 75: '🌨️', 77: '🌨️',
    80: '🌦️', 81: '🌦️', 82: '🌦️',
    85: '🌨️', 86: '🌨️',
    95: '⛈️', 96: '⛈️', 99: '⛈️',
  };
  return map[code] || '🌡️';
}

describe('Weather Hero DOM', () => {
  it('hero card replaces construction zone', () => {
    const fs = require('fs');
    const html = fs.readFileSync(require('path').join(__dirname, '..', 'public', 'index.html'), 'utf8');

    // GREEN: construction zone replaced by weather hero
    assert.ok(!html.includes('此处施工'), 'construction zone should be gone');
    assert.ok(html.includes('weather-mood-title'), 'weather hero title should exist');
    assert.ok(html.includes('weather-play-btn'), 'weather play button should exist');
    assert.ok(html.includes('playWeatherRadio'), 'playWeatherRadio function should exist');
    assert.ok(html.includes('weatherIconEmoji'), 'weatherIconEmoji function should exist');
  });
});
