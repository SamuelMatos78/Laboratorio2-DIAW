alter table public.cars
    alter column registration drop not null,
    alter column plate drop not null;

alter table public.cars
    add column if not exists category varchar(40),
    add column if not exists transmission varchar(30),
    add column if not exists fuel varchar(40),
    add column if not exists daily_price numeric(10, 2),
    add column if not exists image_url varchar(1000),
    add column if not exists available boolean not null default false;

create index if not exists idx_cars_available_category_price
    on public.cars (available, category, daily_price);

insert into public.cars (
    id, registration, year, make, model, plate, category, transmission, fuel, daily_price, image_url, available
) values
    ('00000000-0000-4000-8000-000000000001', null, 2023, 'Chevrolet', 'Onix Premier', null, 'Hatch', 'Automático', 'Flex', 120.00, 'https://images.unsplash.com/photo-1541899481282-d53bffe3c35d?auto=format&fit=crop&w=800&q=80', true),
    ('00000000-0000-4000-8000-000000000002', null, 2024, 'Hyundai', 'HB20 Evolution', null, 'Hatch', 'Manual', 'Flex', 110.00, 'https://images.unsplash.com/photo-1590362891991-f776e747a588?auto=format&fit=crop&w=800&q=80', true),
    ('00000000-0000-4000-8000-000000000003', null, 2023, 'Volkswagen', 'Polo TSI', null, 'Hatch', 'Automático', 'Flex', 130.00, 'https://images.unsplash.com/photo-1552519507-da3b142c6e3d?auto=format&fit=crop&w=800&q=80', true),
    ('00000000-0000-4000-8000-000000000004', null, 2024, 'Toyota', 'Corolla XEi', null, 'Sedan', 'Automático', 'Flex', 220.00, 'https://images.unsplash.com/photo-1621007947382-bb3c3994e3fb?auto=format&fit=crop&w=800&q=80', true),
    ('00000000-0000-4000-8000-000000000005', null, 2023, 'Honda', 'Civic Touring', null, 'Sedan', 'Automático', 'Gasolina', 250.00, 'https://images.unsplash.com/photo-1606016159991-dfe4f2746ad5?auto=format&fit=crop&w=800&q=80', true),
    ('00000000-0000-4000-8000-000000000006', null, 2023, 'Nissan', 'Versa Sense', null, 'Sedan', 'Automático', 'Flex', 150.00, 'https://images.unsplash.com/photo-1617814076367-b759c7d7e738?auto=format&fit=crop&w=800&q=80', true),
    ('00000000-0000-4000-8000-000000000007', null, 2024, 'Jeep', 'Renegade Longitude', null, 'SUV', 'Automático', 'Flex', 210.00, 'https://images.unsplash.com/photo-1533473359331-0135ef1b58bf?auto=format&fit=crop&w=800&q=80', true),
    ('00000000-0000-4000-8000-000000000008', null, 2024, 'Jeep', 'Compass Limited', null, 'SUV', 'Automático', 'Diesel', 290.00, 'https://images.unsplash.com/photo-1563720223185-11003d516935?auto=format&fit=crop&w=800&q=80', true),
    ('00000000-0000-4000-8000-000000000009', null, 2023, 'Volkswagen', 'T-Cross Highline', null, 'SUV', 'Automático', 'Flex', 230.00, 'https://images.unsplash.com/photo-1503376780353-7e6692767b70?auto=format&fit=crop&w=800&q=80', true),
    ('00000000-0000-4000-8000-000000000010', null, 2024, 'Hyundai', 'Creta Ultimate', null, 'SUV', 'Automático', 'Flex', 240.00, 'https://images.unsplash.com/photo-1549399542-7e3f8b79c341?auto=format&fit=crop&w=800&q=80', true),
    ('00000000-0000-4000-8000-000000000011', null, 2023, 'Toyota', 'Hilux SRX 4x4', null, 'Picape', 'Automático', 'Diesel', 450.00, 'https://images.unsplash.com/photo-1559416523-140ddc3d238c?auto=format&fit=crop&w=800&q=80', true),
    ('00000000-0000-4000-8000-000000000012', null, 2024, 'Ford', 'Ranger XLT', null, 'Picape', 'Automático', 'Diesel', 420.00, 'https://images.unsplash.com/photo-1609521263047-f8d205293f24?auto=format&fit=crop&w=800&q=80', true),
    ('00000000-0000-4000-8000-000000000013', null, 2024, 'BMW', '320i M Sport', null, 'Premium', 'Automático', 'Gasolina', 580.00, 'https://images.unsplash.com/photo-1555215695-3004980ad54e?auto=format&fit=crop&w=800&q=80', true),
    ('00000000-0000-4000-8000-000000000014', null, 2023, 'Mercedes-Benz', 'C 300 AMG Line', null, 'Premium', 'Automático', 'Gasolina', 620.00, 'https://images.unsplash.com/photo-1618843479313-40f8afb4b4d8?auto=format&fit=crop&w=800&q=80', true),
    ('00000000-0000-4000-8000-000000000015', null, 2023, 'Audi', 'A4 Performance', null, 'Premium', 'Automático', 'Gasolina', 550.00, 'https://images.unsplash.com/photo-1603584173870-7f23fdae1b7a?auto=format&fit=crop&w=800&q=80', true),
    ('00000000-0000-4000-8000-000000000016', null, 2024, 'Volvo', 'XC60 Recharge', null, 'Elétrico / Híbrido', 'Automático', 'Híbrido Plug-in', 690.00, 'https://images.unsplash.com/photo-1502877338535-766e1452684a?auto=format&fit=crop&w=800&q=80', true),
    ('00000000-0000-4000-8000-000000000017', null, 2024, 'BYD', 'Dolphin EV', null, 'Elétrico', 'Automático', 'Elétrico', 210.00, 'https://images.unsplash.com/photo-1563720223185-11003d516935?auto=format&fit=crop&w=800&q=80', true),
    ('00000000-0000-4000-8000-000000000018', null, 2023, 'Porsche', '911 Carrera S', null, 'Esportivo', 'Automático', 'Gasolina', 1500.00, 'https://images.unsplash.com/photo-1614162692292-7ac56d7f7f1e?auto=format&fit=crop&w=800&q=80', true),
    ('00000000-0000-4000-8000-000000000019', null, 2023, 'Ford', 'Mustang GT 5.0', null, 'Esportivo', 'Automático', 'Gasolina', 1200.00, 'https://images.unsplash.com/photo-1584345604476-8ec5e12e42dd?auto=format&fit=crop&w=800&q=80', true),
    ('00000000-0000-4000-8000-000000000020', null, 2023, 'Renault', 'Kwid Outsider', null, 'Econômico', 'Manual', 'Flex', 85.00, 'https://images.unsplash.com/photo-1542282088-72c9c27ed0cd?auto=format&fit=crop&w=800&q=80', true)
on conflict (id) do update set
    year = excluded.year,
    make = excluded.make,
    model = excluded.model,
    category = excluded.category,
    transmission = excluded.transmission,
    fuel = excluded.fuel,
    daily_price = excluded.daily_price,
    image_url = excluded.image_url,
    available = excluded.available;
