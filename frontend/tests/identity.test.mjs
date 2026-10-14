import test from 'node:test';
import assert from 'node:assert/strict';
import { luhn, validIdentity, validCard, dateFromIdentity } from '../identity.js';

test('Luhn accepts a standard test card and rejects a changed check digit', () => {
  assert.equal(luhn('4242424242424242'), true);
  assert.equal(luhn('4242424242424241'), false);
  assert.equal(luhn('0000000000000000'), false);
  assert.equal(luhn('card-number'), false);
});

test('South African identity checks require checksum and matching real birth date', () => {
  assert.equal(validIdentity('8001015009087', '1980-01-01'), true);
  assert.equal(validIdentity('8001015009088', '1980-01-01'), false);
  assert.equal(validIdentity('8001015009087', '1980-01-02'), false);
  assert.equal(validIdentity('8001015009087', '1980-02-31'), false);
});

test('optional card input supports grouping but never accepts invalid digits', () => {
  assert.equal(validCard(''), true);
  assert.equal(validCard('4242 4242 4242 4242'), true);
  assert.equal(validCard('4242-4242-4242-4242'), true);
  assert.equal(validCard('4242424242424241'), false);
  assert.equal(validCard('4242abc4242424242'), false);
});

test('birth date is inferred only from a valid complete ID', () => {
  assert.equal(dateFromIdentity('8001015009087', '2026-10-14'), '1980-01-01');
  const julyId = Array.from({ length: 10 }, (_, digit) => '900718500908' + digit).find(luhn);
  assert.equal(dateFromIdentity(julyId, '2026-10-14'), '1990-07-18');
  assert.equal(dateFromIdentity('8001015009088', '2026-10-14'), '');
  assert.equal(dateFromIdentity('800101', '2026-10-14'), '');
});

test('birth date inference handles future centuries and invalid calendar dates', () => {
  function identity(prefix) {
    const base = prefix + '500908';
    return Array.from({ length: 10 }, (_, digit) => base + digit).find(luhn);
  }
  assert.equal(dateFromIdentity(identity('050101'), '2026-10-14'), '2005-01-01');
  assert.equal(dateFromIdentity(identity('271201'), '2026-10-14'), '1927-12-01');
  assert.equal(dateFromIdentity(identity('000229'), '2026-10-14'), '2000-02-29');
  assert.equal(dateFromIdentity(identity('800231'), '2026-10-14'), '');
});
